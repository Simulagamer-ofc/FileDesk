# FileDesk — INSTRUCTIONS.md

## 1. Objetivo do projeto

FileDesk é um gerenciador de arquivos Android independente, otimizado principalmente para tablets com teclado e mouse. A experiência visual e de navegação deve ser o mais próxima possível do Explorador de Arquivos do Windows 11, respeitando as limitações e boas práticas do Android.

Este arquivo serve como fonte de verdade para qualquer IA ou desenvolvedor que trabalhar no projeto, incluindo ChatGPT e Gemini.

## 2. Identidade

- Nome: FileDesk
- Pacote Android: `com.simulagamer.filedesk`
- Plataforma: Android
- Linguagem: Kotlin
- UI: Jetpack Compose
- Java: 17
- Min SDK: 26
- Target SDK: 35
- Versão configurada atualmente: 1.4.0
- Version code: 14
- Repositório principal: `Simulagamer-ofc/FileDesk`
- Branch principal: `main`

Não renomear o aplicativo, package, namespace ou repositório sem autorização explícita.

## 3. Direção visual obrigatória

A interface deve seguir o Explorador de Arquivos do Windows 11, especialmente em tablets no modo horizontal.

Prioridades:

- aparência de aplicativo desktop;
- navegação lateral semelhante ao Windows Explorer;
- barra superior e barra de ferramentas;
- breadcrumbs de caminho;
- campo de pesquisa;
- visualização em detalhes com colunas;
- opção de grade;
- abas;
- menus de contexto;
- suporte adequado a mouse e teclado;
- densidade de informação adequada para tela grande;
- temas claro e escuro;
- comportamento responsivo em telas menores.

Evitar transformar o FileDesk em um gerenciador Android genérico com grandes cartões, blocos excessivos ou aparência de aplicativo móvel convencional.

## 4. Estrutura de navegação desejada

A navegação deve priorizar locais realmente úteis ao usuário.

Itens esperados incluem, quando disponíveis:

- Início;
- Área de Trabalho;
- Downloads;
- Documentos;
- Imagens;
- Vídeos;
- Música;
- Lixeira;
- Armazenamento interno;
- Cartão SD;
- unidades USB;
- Rede;
- Configurações.

Não exibir uma quantidade desnecessária de pastas técnicas do Android na tela inicial.

Evitar duplicidade visual entre "FileDesk", "Este dispositivo", "Este Computador" e armazenamento interno. A experiência deve ser simples e próxima do Windows.

## 5. Arquivos e armazenamento

O FileDesk deve trabalhar com arquivos reais do dispositivo, dentro das permissões concedidas pelo Android.

O aplicativo deve:

- acessar armazenamento interno quando autorizado;
- reconhecer cartão SD e USB quando possível;
- permitir abrir arquivos;
- copiar;
- recortar;
- colar;
- renomear;
- excluir com confirmação;
- criar pastas;
- selecionar vários itens;
- pesquisar;
- ordenar;
- mostrar propriedades;
- importar arquivos;
- manter acesso persistente quando o Android permitir.

Não apagar, mover, substituir ou modificar arquivos do usuário silenciosamente.

Operações destrutivas precisam ser claras e previsíveis.

## 6. Integração como gerenciador de arquivos

O objetivo é que o FileDesk possa ser escolhido pelo Android sempre que tecnicamente permitido como aplicativo para manipular/abrir arquivos compatíveis.

Manter e melhorar os intent filters cuidadosamente. Não adicionar filtros excessivamente amplos que prejudiquem outros aplicativos ou façam o FileDesk interceptar ações incorretas.

## 7. Importação automática

Arquivos recebidos pelo FileDesk podem ser organizados automaticamente de acordo com o tipo, desde que a operação seja segura e compreensível.

Estrutura desejada:

- ZIP e compactados → Compactados;
- APK → Aplicativos;
- imagens → Imagens;
- vídeos → Vídeos;
- áudio → Música;
- PDF/documentos/planilhas/apresentações/texto → Documentos;
- outros → Downloads/Outros.

Nunca sobrescrever um arquivo existente sem confirmação ou estratégia segura de nome único.

## 8. ZIP e arquivos compactados

O projeto já possui suporte inicial a ZIP em `ZipUtils.kt`.

Preservar e evoluir:

- identificação de ZIP;
- compactação;
- extração;
- nomes únicos;
- proteção contra caminhos ZIP inseguros;
- suporte a pastas;
- tratamento de erros.

Novos formatos compactados só devem ser adicionados com implementação confiável e sem quebrar ZIP.

## 9. Mouse e teclado

O FileDesk é especialmente voltado para uso em tablet como computador.

Preservar e ampliar suporte a:

- Ctrl+A — selecionar tudo;
- Ctrl+C — copiar;
- Ctrl+X — recortar;
- Ctrl+V — colar;
- Ctrl+T — nova aba;
- F2 — renomear;
- Delete — excluir;
- Enter — abrir;
- clique direito/menu de contexto;
- seleção com mouse;
- navegação eficiente por teclado.

Novos atalhos devem seguir, sempre que possível, os equivalentes do Windows Explorer.

## 10. Abas

As abas devem permanecer como recurso central.

Devem permitir:

- abrir nova aba;
- trocar de aba;
- fechar aba;
- preservar diretório da aba;
- impedir fechamento problemático da última aba;
- apresentar aparência semelhante às abas do Explorer.

Mudanças nessa lógica não devem remover o suporte existente.

## 11. Arquitetura atual importante

Arquivos centrais:

- `MainActivity.kt` — entrada do aplicativo, intents e permissões;
- `ExplorerScreen.kt` — interface e grande parte da lógica do Explorer;
- `ZipUtils.kt` — compactação e extração ZIP;
- `AndroidManifest.xml` — permissões, activity, FileProvider e intent filters;
- `app/build.gradle.kts` — configuração Android e dependências;
- `.github/workflows/build.yml` — build automático do APK.

Antes de modificar um desses arquivos, analisar o código existente inteiro e preservar recursos que já funcionam.

## 12. Regras para alterações por IA

ChatGPT, Gemini ou qualquer outra IA deve:

1. Ler este arquivo antes de alterar o projeto.
2. Examinar o código existente antes de propor substituições.
3. Fazer mudanças incrementais.
4. Não reescrever arquivos grandes sem necessidade.
5. Não remover funcionalidades existentes para simplificar uma implementação.
6. Não alterar package/namespace.
7. Não diminuir versionCode.
8. Atualizar versionCode quando uma nova versão instalável for criada.
9. Manter compatibilidade com Android 26+ salvo decisão explícita em contrário.
10. Verificar imports e dependências.
11. Evitar APIs obsoletas quando houver alternativa apropriada.
12. Não adicionar bibliotecas pesadas sem necessidade.
13. Preservar privacidade e funcionamento local.
14. Não inserir telemetria, publicidade ou rastreamento.
15. Não adicionar serviços externos sem autorização.
16. Não inserir chaves, tokens, senhas ou credenciais no repositório.
17. Não alterar o objetivo visual estilo Windows 11.

## 13. Trabalho simultâneo entre ChatGPT e Gemini

O repositório GitHub é a fonte principal de verdade.

Antes de iniciar uma alteração:

- conferir a versão mais recente da branch;
- identificar quais arquivos serão alterados;
- evitar trabalhar sobre uma cópia antiga.

Depois de uma alteração:

- registrar claramente o que foi modificado;
- informar arquivos alterados;
- manter commits pequenos e descritivos;
- não sobrescrever mudanças recentes de outra IA sem comparação.

Se houver conflito entre uma sugestão de IA e este arquivo, seguir este arquivo até que o usuário determine outra direção.

## 14. Build e APK

O workflow do GitHub deve continuar capaz de gerar o APK de teste.

Antes de considerar uma alteração concluída:

- verificar se o Gradle continua válido;
- verificar Manifest;
- verificar imports Kotlin;
- verificar recursos Android;
- executar ou validar build quando possível;
- corrigir erros antes de declarar a versão pronta.

Não afirmar que um APK funciona se o build não foi concluído com sucesso.

## 15. Privacidade

O FileDesk deve funcionar prioritariamente de forma local.

Não enviar nomes, conteúdo, metadados ou lista de arquivos para servidores externos.

Não adicionar analytics, anúncios ou coleta de dados sem autorização explícita.

## 16. Segurança

Especial atenção para:

- exclusão de arquivos;
- movimentação;
- sobrescrita;
- extração ZIP;
- URIs externas;
- permissões de armazenamento;
- FileProvider;
- intents recebidos.

Nunca confiar cegamente em nome de arquivo, extensão, MIME type ou caminho fornecido externamente.

## 17. Prioridades de desenvolvimento

Ordem geral:

1. estabilidade e instalação;
2. acesso correto aos arquivos;
3. operações fundamentais;
4. aparência fiel ao Windows Explorer;
5. mouse e teclado;
6. ZIP/compactados;
7. integração com Android;
8. refinamentos de produtividade;
9. recursos de rede/nuvem apenas quando a base estiver sólida.

## 18. Resultado esperado

O objetivo final é um FileDesk que, ao ser aberto em um tablet Android no modo horizontal com teclado e mouse, transmita a sensação de usar o Explorador de Arquivos de um PC, sem tentar transformar o Android em Windows e sem sacrificar segurança ou compatibilidade.

A interface deve ser limpa, profissional, rápida e familiar, com acesso fácil às funções mais importantes e sem pastas ou elementos redundantes na tela principal.
