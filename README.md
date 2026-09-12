# FileDesk 1.0

Gerenciador de arquivos Android independente, inspirado no Explorador de Arquivos do Windows 11 e otimizado para tablets com toque, mouse e teclado.

## Recursos da versão 1.0

- Acesso ao armazenamento pelo Storage Access Framework do Android.
- Vários locais autorizados: armazenamento interno, cartão SD e unidades USB.
- Permissões persistentes para não precisar autorizar novamente a cada abertura.
- Abas de navegação.
- Histórico Voltar / Avançar / Subir.
- Pesquisa na pasta atual.
- Visualização em Detalhes ou Grade.
- Classificação por nome, data, tipo ou tamanho.
- Seleção múltipla.
- Copiar, recortar e colar arquivos e pastas.
- Cópia recursiva de pastas.
- Renomear.
- Excluir com confirmação.
- Criar nova pasta.
- Abrir arquivos em aplicativos Android compatíveis.
- Janela de propriedades.
- Acesso rápido a Downloads, Documentos, Imagens, Vídeos e Música quando disponíveis.
- Tema claro e escuro.
- Layout adaptado para tablet e celular.
- Ícone próprio do FileDesk.

## Atalhos de teclado

| Atalho | Ação |
| --- | --- |
| Ctrl + A | Selecionar tudo |
| Ctrl + C | Copiar |
| Ctrl + X | Recortar |
| Ctrl + V | Colar |
| Ctrl + T | Nova aba |
| F2 | Renomear |
| Delete | Excluir |
| Enter | Abrir item selecionado |

## Android

- Package: `com.simulagamer.filedesk`
- Min SDK: 26
- Target SDK: 35
- Versão: 1.0.0
- Java: 17
- UI: Jetpack Compose

## Privacidade

O FileDesk não envia arquivos para servidores. O acesso aos arquivos depende das permissões de pasta concedidas pelo próprio seletor de arquivos do Android.

## Build

O GitHub Actions compila automaticamente um APK de teste e publica o arquivo como artefato do workflow.
