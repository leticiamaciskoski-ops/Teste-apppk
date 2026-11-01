# Autoclick Pesca — ferramenta de teste para o seu minigame

App Android que joga o seu minigame sozinho para você testar balanceamento: **segura** para lançar, **espera o sinal de fisgada** (lendo a cor de um pixel da tela) e dá os **toques rápidos** (13 por padrão), repetindo sem parar. Mostra estatísticas da espera do jogo e do tempo real dos toques.

## Estado (31/10/2026)

- **O `.apk` NÃO foi gerado.** O ambiente onde trabalhei não tem Android SDK e a rede bloqueia o download. Veja "Como gerar o APK".
- Código escrito e **compilado apenas contra stubs de API** que escrevi de memória: isso pega erros de sintaxe e de tipos, mas **não garante** que as chamadas existam com essa assinatura no Android real. Nunca rodou em aparelho.
- A lógica pura (cores, lotes de toques, configuração, estatísticas) tem 31 verificações passando na JVM: `./tests/run-tests.sh`.

## Como gerar o APK

1. **GitHub:** envie esta pasta para um repositório, abra *Actions → Build APK Autoclick → Run workflow* e baixe o artefato `autoclick-pesca-apk`.
2. **Android Studio:** *File > Open* nesta pasta → *Build > Build APK(s)*.
3. **Terminal:** `./gradlew assembleDebug` (JDK 17 + Android SDK 34).

Requer **Android 11 ou superior** (a leitura de cor usa a captura de tela da acessibilidade).

## Como usar

1. Instale e abra o app. Toque em **Abrir ajustes de Acessibilidade** e ative **Autoclick Pesca**. No Android 13+, se estiver bloqueado: Informações do app → menu ⋮ → *Permitir configurações restritas*.
2. Ajuste os parâmetros e toque em **Salvar**.
3. Abra o seu jogo. Aparece um painel flutuante (arraste pelo título). Toque em **Calibrar** e siga os 3 passos: onde segurar, onde tocar, e **sobre o sinal de fisgada** (com o sinal visível na tela).
4. Toque em **Testar**: o painel mostra ao vivo a cor lida e se o sinal está sendo **DETECTADO**. Confira antes de iniciar.
5. Toque em **Iniciar**. Deixe a tela acesa (desative o bloqueio automático em Opções do desenvolvedor → "Permanecer ativo").

## Parâmetros (tela inicial do app)

| Parâmetro | Para que serve |
|---|---|
| Segurar para lançar | Tempo do toque longo. Acerte a zona perfeita da sua barra |
| Variação aleatória | Sorteia o segurar ± N ms: mede a tolerância da zona perfeita |
| Quantidade de toques / Intervalo / Duração | O "13 toques" e a velocidade deles |
| Janela do jogo | Só para o app avisar se os toques estouraram (ex.: 500 ms) |
| Tolerância de cor | Diferença máxima por canal RGB para considerar o sinal detectado |
| Intervalo de leitura | De quanto em quanto tempo olha a tela (mínimo ~340 ms) |
| Tempo máximo esperando | Desiste do ciclo se o sinal nunca aparecer |
| Atraso antes dos toques | Simula o tempo de reação de um jogador |
| Pausa entre ciclos | Espera depois dos toques antes de lançar de novo |

## Limitações importantes (leia)

- **Os 13 toques vão em 2 gestos** (10 + 3): o Android limita cada gesto a 10 traços, então há uma pequena pausa de latência no meio. A estimativa do app usa 60 ms para isso, que é um **chute**; o app mede o tempo real e mostra "ESTOUROU" se passar da janela. Se o seu jogo é rígido nos 500 ms, olhe esse número.
- **Detecção por leitura de tela:** o sistema limita a cerca de 3 capturas por segundo, então o sinal é percebido com até ~350 ms de atraso, e **um sinal que pisca rápido demais pode passar despercebido**. Prefira calibrar num elemento que fica visível por ~1 s ou mais. Se o seu jogo puder expor um sinal fixo (ex.: um quadradinho que muda de cor durante a fisgada), a detecção fica bem mais confiável.
- A fisgada é medida como "tempo desde que terminou o segurar", então inclui esse atraso de leitura.
- Jogos que bloqueiam captura de tela (FLAG_SECURE) devolvem imagem preta e a detecção não funciona.
- Este app é para testar o **seu próprio jogo**. O serviço de acessibilidade só toca e lê um pixel; não coleta nem envia dados e não pede internet.

## Próximos passos sugeridos

1. Gerar o APK e rodar o **Testar** no seu jogo; se a captura falhar, me mande a mensagem do painel.
2. Conferir o tempo real dos 13 toques; se estourar, aumentar a pressa (intervalo menor) ou me pedir para reduzir a pausa entre lotes.
3. Se a detecção por cor for instável, trocar por um sinal fixo no jogo ou adicionar modo de tempo fixo (não implementado).
4. Opcional: exportar o log/estatísticas para arquivo para análises longas.

## Arquivos

```
app/src/main/java/com/meujogo/autoclick/
  Config.java, ColorMatch.java, TapPlan.java, RunStats.java   # lógica pura (testada)
  ConfigStore.java      # salvar/ler configuração
  AutoClickService.java # serviço de acessibilidade: gestos, captura, painel, calibração
  MainActivity.java     # tela de configuração
tests/                  # run-tests.sh + AutoClickLogicTest.java
.github/workflows/build-apk.yml
```
