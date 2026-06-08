# Apagão

**Apagão** é um MVP de jogo Android 2D feito em Java nativo, sem Unity e sem bibliotecas pesadas. A versão inicial usa `Canvas` em uma `SurfaceView` para validar a mecânica principal:

> Você só enxerga quando acende a luz. Mas a luz atrai o perigo.

## Status do MVP

Esta primeira versão jogável inclui:

- Projeto Android nativo com módulo `app`.
- `MainActivity` em Java.
- `GameView` desenhando tudo com `Canvas`.
- Jogador com movimentação por toque/arraste.
- Labirinto simples baseado em grade.
- Pulso de luz temporário para revelar a fase.
- Bateria com custo por pulso e recarga quando a luz está apagada.
- Inimigos que patrulham e são atraídos pelo último pulso de luz.
- Saída da fase, vitória ao completar todas as fases e derrota ao tocar em inimigos.
- 3 fases simples para testar a progressão.

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

> Observação: se o AndroidIDE instalado não suportar a versão configurada do Android Gradle Plugin, atualize o AndroidIDE ou ajuste a versão do plugin em `build.gradle` para uma versão compatível com o ambiente local.

## Como compilar pela linha de comando

Com Android SDK instalado e configurado:

```bash
gradle :app:assembleDebug
```

Também é possível usar o Gradle integrado do Android Studio. O projeto usa repositórios `google()` e `mavenCentral()` para baixar o Android Gradle Plugin.

## Como jogar

- O objetivo é chegar ao quadrado verde de saída.
- Arraste no lado esquerdo da tela para mover o jogador azul.
- O labirinto fica quase todo escuro quando a luz está apagada.
- Toque no botão **LUZ** para emitir um pulso de luz e revelar a área ao redor.
- Cada pulso consome bateria.
- A bateria recarrega aos poucos quando a luz não está ativa.
- Inimigos vermelhos são atraídos para o local onde a luz foi acesa.
- Se um inimigo encostar no jogador, é derrota.
- Complete as 3 fases para vencer.

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

- Ajustar balanceamento de bateria, raio de luz e velocidade dos inimigos após testes.
- Adicionar áudio simples para pulso de luz e alerta de inimigo.
- Criar sprites leves para paredes, jogador e inimigos.
- Salvar progresso local.
- Adicionar mais fases e um menu inicial.
