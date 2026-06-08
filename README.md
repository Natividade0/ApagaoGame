# Mira Real

**Mira Real** é um MVP de jogo Android 2D feito em Java nativo, sem Unity e sem bibliotecas pesadas. A versão inicial usa `Canvas` em uma `SurfaceView` para validar uma mecânica simples e viciante:

> Arraste, mire, solte e acerte o alvo usando física.

## Status do MVP

Esta primeira versão jogável inclui:

- Projeto Android nativo com módulo `app`.
- `MainActivity` em Java.
- `GameView` desenhando tudo com `Canvas`.
- Física simples com gravidade, vento, quique, atrito e obstáculos.
- Sistema de arrastar para mirar e soltar para arremessar.
- Diferentes tipos de desafios:
  - flecha no alvo;
  - bolinha no cesto;
  - pedra na lâmpada;
  - copo em movimento;
  - trickshot com obstáculos.
- 8 fases iniciais.
- Sistema simples de tentativas e estrelas.

## Como abrir no Android Studio

1. Clone o repositório:

   ```bash
   git clone https://github.com/Natividade0/ApagaoGame.git
   cd ApagaoGame
   ```

2. Abra o Android Studio.
3. Selecione **Open** e escolha a pasta do projeto.
4. Aguarde a sincronização do Gradle.
5. Conecte um dispositivo Android ou inicie um emulador.
6. Execute o módulo **app** com **Run**.

## Como abrir no AndroidIDE

1. Copie ou clone o repositório no dispositivo Android.
2. Abra o AndroidIDE.
3. Escolha **Open Project** e selecione a pasta do repositório.
4. Aguarde a sincronização do Gradle.
5. Compile e execute o módulo **app**.

## Atualizar no AndroidIDE

Se você já clonou o projeto antes, rode:

```bash
cd /storage/emulated/0/AndroidIDEProjects/ApagaoGame
git fetch --all
git reset --hard origin/main
rm -rf .gradle app/build build
```

Depois rode pelo botão **Build/Run** do AndroidIDE.

## Como jogar

- Arraste o dedo para trás a partir do lançador.
- Quanto mais puxar, mais força o arremesso terá.
- Solte para lançar.
- Acerte o alvo da fase.
- Algumas fases têm vento, alvos em movimento, obstáculos ou quique.
- Toque depois de errar para tentar novamente.
- Toque depois de acertar para avançar de fase.

## Estrutura principal

```text
app/
  build.gradle
  src/main/
    AndroidManifest.xml
    java/com/natividade0/apagaogame/
      MainActivity.java
      GameView.java
    res/values/
      strings.xml
      colors.xml
      styles.xml
```

## Próximos passos sugeridos

- Ajustar a sensação do arremesso.
- Adicionar sons de acerto, erro, vidro quebrando e quique.
- Criar sprites melhores para objetos e alvos.
- Adicionar menu inicial.
- Adicionar seleção de fases.
- Salvar progresso local.
- Criar modo infinito e desafio diário.
