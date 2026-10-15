# Qwen Local Chat

Android app que baixa e executa **Qwen2.5-3B-Instruct GGUF Q4_K_M** localmente com o binding Android oficial do `llama.cpp`.

## Fluxo do app

1. Primeiro boot: tela **Baixar e instalar modelo**.
2. Baixa ~2,1 GB do Hugging Face com suporte a retomada (`.part` + HTTP Range).
3. Move o GGUF para o armazenamento privado persistente do app.
4. Carrega o modelo via `llama.cpp`.
5. Abre a interface de conversa inspirada no WhatsApp.
6. Próximos boots: pula o download e abre direto a conversa.

O histórico é salvo em `filesDir/conversation.json`. O modelo fica em `filesDir/models/` e não é baixado de novo enquanto estiver instalado.

> Observação: o arquivo do modelo e o histórico persistem entre aberturas do app, mas a RAM/KV-cache do modelo precisa ser recriada quando o processo Android é encerrado. Para manter contexto após um reinício, o app injeta uma janela recente da conversa na primeira mensagem.

## Compilar no GitHub

1. Crie um repositório vazio.
2. Envie todos os arquivos deste projeto para a raiz.
3. Faça `git push`.
4. Abra **Actions > Build Android APK**.
5. Ao terminar, baixe o artifact `bubblebee-apk`.

O workflow usa **Ubuntu Linux**, Java 17, Android SDK 36, NDK 29, CMake 3.31.6 e **Gradle 8.14.3**, e baixa `llama.cpp v0.6.0` automaticamente.

## Compilar localmente em Linux

```bash
bash scripts/fetch-llama.sh
# Instale Android SDK 36, NDK 29.0.13113456 e CMake 3.31.6
gradle :app:assembleDebug --no-daemon
```

## Modelo

- Repositório: `Qwen/Qwen2.5-3B-Instruct-GGUF`
- Arquivo: `qwen2.5-3b-instruct-q4_k_m.gguf`
- Quantização: Q4_K_M
- Tamanho aproximado: 2,1 GB

## Requisitos atuais

- Android 13+ (`minSdk 33`) por causa do binding Android atual do llama.cpp usado pelo projeto.
- ABI: `arm64-v8a`.
- Recomendado: pelo menos 6 GB de RAM; 8 GB ou mais é melhor para 3B Q4 e contexto de conversa.
- Armazenamento livre recomendado: 3 GB ou mais.
