param([switch]$Reiniciar)
$ErrorActionPreference='Stop'
$raiz=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $raiz

$etiquetas=(docker inspect boston-aula-postgres-1 --format '{{json .Config.Labels}}' | ConvertFrom-Json)
if($LASTEXITCODE -ne 0 -or $etiquetas.'com.docker.compose.project' -ne 'boston-aula' -or
   -not [string]::Equals($etiquetas.'com.docker.compose.project.working_dir',$raiz,[StringComparison]::OrdinalIgnoreCase)) {
  throw 'El contenedor PostgreSQL no pertenece a este proyecto local.'
}
$config=(docker compose config --format json | ConvertFrom-Json)
foreach($servicio in @('postgres','api-erp','api-aula','identidad','seaweedfs')) {
  if(-not $config.services.PSObject.Properties[$servicio]) { throw "Servicio local faltante: $servicio" }
}
$urls=@($config.services.'api-aula'.environment.BOSTON_ERP_URL,$config.services.'api-aula'.environment.BOSTON_S3_ENDPOINT,
        $config.services.'api-aula'.environment.BOSTON_TOKEN_URI,$config.services.'api-erp'.environment.BOSTON_JWKS_URI)
foreach($url in $urls) {
  if($url -notmatch '^http://(api-erp|seaweedfs|identidad):') { throw "Dependencia no local: $url" }
}

function Ejecutar-Sql([string]$base,[string]$archivo,[string[]]$variables=@()) {
  $origen=(Resolve-Path (Join-Path $PSScriptRoot $archivo)).Path
  if(-not $origen.StartsWith($raiz+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'SQL fuera del proyecto' }
  $destino="/tmp/$archivo"
  docker cp $origen "boston-aula-postgres-1:$destino" | Out-Null
  if($LASTEXITCODE -ne 0) { throw "No se pudo copiar $archivo" }
  $argsSql=@('compose','exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U','boston','-d',$base)
  foreach($variable in $variables) { $argsSql+=@('-v',$variable) }
  $argsSql+=@('-f',$destino)
  & docker @argsSql
  if($LASTEXITCODE -ne 0) { throw "Falló $archivo en $base" }
}

function Consultar([string]$base,[string]$sql) {
  $salida=docker compose exec -T postgres psql -U boston -d $base -Atc $sql
  if($LASTEXITCODE -ne 0) { throw "Consulta fallida en $base" }
  return ($salida | Out-String).Trim()
}

$coordinacion=Consultar 'identidad_boston' "SELECT id FROM cuentas WHERE usuario='coordinacion.demo' AND activo"
$docente=Consultar 'identidad_boston' "SELECT id FROM cuentas WHERE usuario='docente.demo' AND activo"
if($coordinacion -notmatch '^[a-f0-9-]{36}$' -or $docente -notmatch '^[a-f0-9-]{36}$') { throw 'Cuentas de prueba no disponibles' }

if($Reiniciar) {
  $copias=Join-Path $PSScriptRoot '.cp05-backup'
  foreach($base in @('erp_boston','aula_boston','identidad_boston')) {
    $copia=Join-Path $copias "$base.dump"
    if(-not (Test-Path -LiteralPath $copia) -or (Get-Item -LiteralPath $copia).Length -lt 10000) {
      throw "Falta copia de seguridad verificada: $base"
    }
  }
  docker compose stop api-aula api-erp | Out-Null
  if($LASTEXITCODE -ne 0) { throw 'No se pudieron detener los servicios académicos' }
  Ejecutar-Sql 'aula_boston' 'cp05-reset-aula.sql'
  Ejecutar-Sql 'erp_boston' 'cp05-reset-erp.sql'
  Ejecutar-Sql 'identidad_boston' 'cp05-identidad.sql'
}

Ejecutar-Sql 'erp_boston' 'cp05-seed-erp.sql' @("coord_id=$coordinacion")
docker compose up -d --no-deps api-erp api-aula | Out-Null
if($LASTEXITCODE -ne 0) { throw 'No arrancaron los servicios académicos' }

$sincronizado=$false
for($i=0;$i -lt 40;$i++) {
  Start-Sleep -Seconds 2
  $conteo=Consultar 'aula_boston' "SELECT count(*) FROM cursos WHERE id IN (SELECT md5('cp05:oferta:'||s.codigo||':'||a.nombre)::uuid FROM (VALUES ('P5A'),('P5B'),('S5A')) s(codigo) CROSS JOIN (VALUES ('Matemática'),('Comunicación'),('Inglés')) a(nombre))"
  if($conteo -eq '9') { $sincronizado=$true; break }
}
if(-not $sincronizado) { throw 'No se completó la proyección ERP→Aula' }

Ejecutar-Sql 'aula_boston' 'cp05-seed-aula.sql' @("docente_id=$docente")
$salida=python (Join-Path $PSScriptRoot 'cp05-archivos-local.py')
if($LASTEXITCODE -ne 0) { throw 'Falló la carga de archivos en S3 local' }
$medidas=@{}
foreach($linea in $salida) { if($linea -match '^(pdf|docx|pptx)_size=(\d+)$') { $medidas[$Matches[1]]=$Matches[2] } }
if($medidas.Count -ne 3) { throw 'No se obtuvieron los tamaños de los archivos ficticios' }
Ejecutar-Sql 'aula_boston' 'cp05-seed-archivos.sql' @("pdf_size=$($medidas.pdf)","docx_size=$($medidas.docx)","pptx_size=$($medidas.pptx)")

Write-Output "ERP: $(Consultar 'erp_boston' 'SELECT count(*) FROM estudiantes') estudiantes, $(Consultar 'erp_boston' 'SELECT count(*) FROM ofertas_academicas') cursos"
Write-Output "Aula: $(Consultar 'aula_boston' 'SELECT count(*) FROM cursos') cursos, $(Consultar 'aula_boston' 'SELECT count(*) FROM tareas') tareas, $(Consultar 'aula_boston' 'SELECT count(*) FROM materiales_curso') materiales"
Write-Output 'Datos CP-05 listos en http://localhost:8088/'
