import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({plugins:[react()],server:{proxy:{'/api':'http://localhost:8088','/oauth2':'http://localhost:8088','/login':'http://localhost:8088','/logout':'http://localhost:8088'}}});
