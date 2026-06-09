# Mira Real

**Mira Real** é a versão 0.3 do projeto Android nativo iniciado como Apagão. O jogo foi reformulado para um casual mobile de arremesso em 2D, feito em **Java + Canvas + SurfaceView**, sem Unity, Godot, LibGDX ou bibliotecas pesadas.

A ideia agora é simples e mais apresentável: arraste para trás, mire, solte e tente acertar objetos reais em cenas leves e bem-humoradas.

## Status da versão 0.3

Esta versão inclui:

- Projeto Android nativo com módulo `app`.
- `MainActivity` fullscreen em Java.
- `GameView` com `SurfaceView`, loop próprio e renderização por `Canvas`.
- Menu inicial com opções **Jogar** e **Como jogar**.
- Tela de objetivo antes de cada fase.
- Sistema de arremesso: arrastar para trás, mirar e soltar.
- Linha pontilhada de trajetória prevista.
- Barra de força durante a mira.
- Física 2D simples com gravidade, vento, peso, quique e rotação por tipo de objeto.
- Objetos com identidade visual desenhada no Canvas: bolinha de papel, pedra, bola e moeda.
- Cenários simples com contexto real, chão, parede, mesa, muro, caixas, casa, garagem e sombras.
- Reações visuais ao acertar: lixeira balançando, lâmpada quebrando, lata caindo, copo tremendo, campainha acendendo e alvo vibrando.
- Partículas simples para papel, vidro, poeira e faíscas.
- Tremida leve de tela no impacto.
- Sistema de tentativas e estrelas.
- Feedback de erro com **Quase!** e tentativa rápida novamente.
- Vitória ao completar as 6 fases.

## Fases

1. **Escritório**
   - Objeto: bolinha de papel.
   - Objetivo: acertar a lixeira.
   - Reação: papéis e partículas saltam da lixeira.

2. **Quintal à noite**
   - Objeto: pedra.
   - Objetivo: quebrar a lâmpada.
   - Reação: vidro, tremida e ambiente mais escuro.

3. **Lata no muro**
   - Objeto: bola.
   - Objetivo: derrubar a lata.
   - Reação: lata gira/cai e aparece poeira de impacto.

4. **Copo na mesa**
   - Objeto: moeda.
   - Objetivo: cair dentro do copo.
   - Reação: copo balança e mostra feedback visual.

5. **Campainha distante**
   - Objeto: pedra pequena.
   - Objetivo: acertar a campainha.
   - Reação: campainha acende e solta faíscas.

6. **Trickshot de garagem**
   - Objeto: bola.
   - Objetivo: quicar na caixa e acertar o alvo final.
   - Reação: caixa treme, alvo reage e aparecem partículas.

## Como jogar

- Toque em **Jogar** no menu inicial.
- Leia o objetivo da fase e toque para começar.
- Toque e arraste o objeto para trás.
- Use a linha pontilhada para prever a trajetória.
- Use a barra de força para controlar o lançamento.
- Solte para arremessar.
- Se errar, toque e tente novamente.
- Ao acertar, toque para avançar.

### Estrelas

- **3 estrelas:** acerto de primeira.
- **2 estrelas:** acerto em até 3 tentativas.
- **1 estrela:** acerto com mais tentativas.

## Como abrir no Android Studio

1. Clone o repositório:

   ```bash
   git clone https://github.com/Natividade0/ApagaoGame.git
   cd ApagaoGame
   ```

2. Abra o Android Studio.
3. Clique em **Open**.
4. Selecione a pasta do projeto.
5. Aguarde o **Gradle Sync**.
6. Para testar no aparelho/emulador, selecione o módulo **app** e clique em **Run ▶**.
7. Para gerar APK debug, use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

O APK debug será gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Como abrir no AndroidIDE

1. Abra o AndroidIDE.
2. Toque em **Open Project**.
3. Selecione a pasta do repositório.
4. Aguarde a sincronização do Gradle.
5. Para compilar, use a ação de build do AndroidIDE ou rode:

   ```bash
   gradle :app:assembleDebug
   ```

6. O APK debug ficará em:

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

> Se o AndroidIDE instalado não suportar a versão configurada do Android Gradle Plugin, atualize o AndroidIDE ou ajuste a versão do plugin em `build.gradle` conforme a versão suportada pelo seu ambiente.

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

## Observações técnicas

- O jogo continua totalmente em Java nativo.
- A renderização usa Canvas dentro de uma SurfaceView.
- Não há dependência de sprites, imagens externas ou engines.
- O método principal de desenho no `GameView` é `drawGame(Canvas canvas)`, evitando conflito com métodos herdados do Android.
- A física é propositalmente simples para manter estabilidade e previsibilidade.
