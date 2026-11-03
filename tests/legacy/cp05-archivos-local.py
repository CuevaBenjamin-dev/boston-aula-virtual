"""Crea y carga archivos ficticios de CP-05 en el S3 local, sin dependencias externas."""
import datetime
import hashlib
import hmac
import io
import os
from pathlib import Path
from urllib.request import Request, urlopen
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
ENV = {}
for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines() if (ROOT / ".env").exists() else []:
    if "=" in line and not line.lstrip().startswith("#"):
        k, v = line.split("=", 1)
        ENV[k.strip()] = v.strip().strip('"').strip("'")
ACCESS = os.getenv("S3_ACCESS_KEY", ENV.get("S3_ACCESS_KEY", "local-aula"))
SECRET = os.getenv("S3_SECRET_KEY", ENV.get("S3_SECRET_KEY", "cambiar-local-s3"))
HOST = "localhost:8333"
BUCKET = "aula-privada"


def pdf(text):
    stream = f"BT /F1 18 Tf 50 750 Td ({text}) Tj ET".encode("ascii")
    parts = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    result = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for n, part in enumerate(parts, 1):
        offsets.append(len(result))
        result.extend(f"{n} 0 obj\n".encode() + part + b"\nendobj\n")
    xref = len(result)
    result.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for off in offsets[1:]:
        result.extend(f"{off:010d} 00000 n \n".encode())
    result.extend(f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return bytes(result)


def office(kind):
    out = io.BytesIO()
    with ZipFile(out, "w", ZIP_DEFLATED) as z:
        if kind == "docx":
            z.writestr("[Content_Types].xml", '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
            z.writestr("_rels/.rels", '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
            z.writestr("word/document.xml", '<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Ficha ficticia de problemas matematicos CP-05</w:t></w:r></w:p><w:sectPr/></w:body></w:document>')
        else:
            z.writestr("[Content_Types].xml", '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>')
            z.writestr("_rels/.rels", '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>')
            z.writestr("ppt/presentation.xml", '<?xml version="1.0"?><p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst><p:sldSz cx="9144000" cy="6858000"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>')
            z.writestr("ppt/_rels/presentation.xml.rels", '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>')
            z.writestr("ppt/slides/slide1.xml", '<?xml version="1.0"?><p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:sp><p:nvSpPr><p:cNvPr id="2" name="Titulo"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="es-PE"/><a:t>Funciones cuadraticas CP-05</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>')
    return out.getvalue()


def sign_put(key, content, mime):
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    day = timestamp[:8]
    path = f"/{BUCKET}/{key}"
    payload_hash = hashlib.sha256(content).hexdigest()
    signed = "content-type;host;x-amz-content-sha256;x-amz-date"
    canonical = f"PUT\n{path}\n\ncontent-type:{mime}\nhost:{HOST}\nx-amz-content-sha256:{payload_hash}\nx-amz-date:{timestamp}\n\n{signed}\n{payload_hash}"
    scope = f"{day}/us-east-1/s3/aws4_request"
    string_to_sign = f"AWS4-HMAC-SHA256\n{timestamp}\n{scope}\n{hashlib.sha256(canonical.encode()).hexdigest()}"
    def sha(key_bytes, value):
        return hmac.new(key_bytes, value.encode(), hashlib.sha256).digest()
    signing_key = sha(sha(sha(sha(("AWS4" + SECRET).encode(), day), "us-east-1"), "s3"), "aws4_request")
    signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()
    authorization = f"AWS4-HMAC-SHA256 Credential={ACCESS}/{scope}, SignedHeaders={signed}, Signature={signature}"
    req = Request(f"http://{HOST}{path}", data=content, method="PUT", headers={
        "Content-Type": mime,
        "Host": HOST,
        "x-amz-date": timestamp,
        "x-amz-content-sha256": payload_hash,
        "Authorization": authorization,
    })
    with urlopen(req, timeout=20) as response:
        if response.status != 200:
            raise RuntimeError(f"S3 local respondió {response.status} para {key}")


files = {
    "pdf": (pdf("Guia ficticia de fracciones Boston CP-05"), "application/pdf"),
    "docx": (office("docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    "pptx": (office("pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
}
for key, kind in [
    ("materiales/cp05/guia.pdf", "pdf"),
    ("materiales/cp05/ficha.docx", "docx"),
    ("materiales/cp05/funciones.pptx", "pptx"),
    ("materiales/cp05/oculto.pdf", "pdf"),
    ("tareas/cp05/fracciones.pdf", "pdf"),
    ("tareas/cp05/fracciones.docx", "docx"),
]:
    data, mime = files[kind]
    sign_put(key, data, mime)
for kind, (data, _) in files.items():
    print(f"{kind}_size={len(data)}")
