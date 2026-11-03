# Autoclick Pesca (v0.2) — autoclick + server hop do Roblox

App Android com painel **flutuante** que fica por cima do jogo. Ele faz duas coisas:

1. **Autoclick** do minigame: segura para lançar, espera o sinal de fisgada (lendo a cor de um pixel) e dá os toques rápidos, repetindo sem parar.
2. **Server hop**: troca de servidor direto pelo app, escolhendo servidores quase vazios (por padrão **1 a 2 jogadores**) no **Steal An Egg**.

## Atualizando da v0.1 (leia antes de instalar)

A v0.2 usa uma chave de assinatura fixa. Por isso, **desinstale a versão antiga uma única vez** antes de instalar esta (senão o Android recusa a instalação por "conflito"). Você vai precisar reativar a acessibilidade e calibrar de novo. As próximas versões já instalam por cima, sem desinstalar.

## Como usar o server hop

1. Abra o app e vá até a seção **Server hop (Roblox)**. O jogo padrão já é o **Steal An Egg** (Place ID 107778070777162, confirmado na API do Roblox).
2. Toque em **Permitir exibir sobre outros apps** e ative (recomendado: sem isso o Android pode impedir o painel de abrir o Roblox em segundo plano).
3. Abra o Roblox. No painel flutuante, toque em **Server hop (1-2 jogadores)**. O app busca um servidor pequeno e abre o Roblox direto nele.
4. O botão **–** do painel recolhe tudo numa barrinha (**+** expande de novo) para não cobrir o jogo. Arraste pelo título.

Para testar sem o painel: na tela do app, toque em **Testar server hop agora**.

Para usar **outro jogo**: cole o link (`roblox.com/games/...`) ou o número no campo e toque em **Verificar e usar este jogo**. Aceita ID de lugar ou de universo.

## Limites e comportamento (medidos na API real)

- A lista do Roblox vem **dos menores para os maiores**: hoje as primeiras centenas de servidores do Steal An Egg têm **1 jogador**. Servidores com exatamente 2 jogadores ficam bem mais para trás na lista, então na prática o hop costuma cair em servidores de 1 jogador (que está dentro de "1-2").
- O Roblox permite só **~3 consultas por minuto** nessa lista. O app guarda a lista por **45 s** e sorteia os próximos servidores dela, então dá para dar vários hops seguidos com uma única consulta. Se bater no limite, ele espera sozinho (até 30 s) ou avisa quanto falta.
- Servidores já visitados são evitados nos hops seguintes. Um servidor pode encher ou fechar entre a consulta e a entrada; se cair num cheio, é só dar hop de novo.

## Estado e o que NÃO foi testado

- O APK compila no GitHub e os testes de lógica passam (inclusive um teste real contra a API do Roblox no servidor do GitHub).
- **Nunca rodou em aparelho.** Itens para você conferir no celular, em ordem:
  1. O link `roblox://experiences/start?placeId=...&gameInstanceId=...` abre o Roblox **no servidor escolhido** (e não só no jogo). Se abrir só o jogo, me avise e eu troco o formato do link.
  2. O botão do painel consegue abrir o Roblox com o app em segundo plano (depende da permissão de exibir sobre outros apps).
  3. O painel recolhido/expandido não atrapalha o toque no jogo.
- A calibração do autoclick e a detecção por cor continuam como na v0.1 (veja as limitações abaixo).

## Autoclick: como usar (resumo)

1. Ative a acessibilidade (Ajustes → Acessibilidade → Apps instalados → Autoclick Pesca). No Android 13+, se estiver bloqueado: Informações do app → ⋮ → **Permitir configurações restritas**.
2. No painel: **Calibrar** (3 toques: onde segurar, onde tocar, e sobre o sinal de fisgada), depois **Testar** para conferir a detecção e **Iniciar**.
3. Parâmetros (tempo de segurar, toques, intervalo, tolerância de cor etc.) ficam na tela do app.

Limitações: os 13 toques vão em 2 gestos (o Android limita a 10 traços por gesto); a detecção por cor lê a tela ~3 vezes por segundo, então sinais que piscam rápido demais podem passar; jogos que bloqueiam captura de tela devolvem imagem preta.

## Aviso

Server hop usa só a API pública do Roblox e o link de entrada em servidor; não mexe no jogo. Mesmo assim, **automatizar ações (autoclick) em jogos de terceiros pode ir contra as regras do jogo ou do Roblox e arriscar a sua conta**. Use por sua conta e risco; este projeto é uma ferramenta de teste.

## Como gerar o APK

Cada envio ao GitHub gera o APK sozinho e o publica na aba **Releases** do repositório. Também dá para abrir a pasta no Android Studio (*Build → Build APK(s)*) ou rodar `./gradlew assembleDebug`.

## Testes (sem Android)

- `./tests/run-tests.sh` — lógica pura (autoclick + server hop), só precisa de JDK.
- `./tests/run-api-tests.sh /caminho/json.jar offline|live` — parse da API do Roblox (offline) e consulta real (live). Precisa do `org.json`.

## Arquivos principais

```
app/src/main/java/com/meujogo/autoclick/
  ServerHop.java, ServerPool.java      # lógica pura do hop (testada)
  RobloxApi.java                       # consultas à API pública do Roblox
  HopController.java                   # busca o servidor e abre o Roblox
  Config.java, ConfigStore.java        # configuração (inclui Place ID e jogadores min/max)
  AutoClickService.java                # painel flutuante, gestos, captura de tela, calibração
  MainActivity.java                    # tela de configuração
debug.keystore                         # chave fixa de teste (para atualizar por cima)
.github/workflows/build-apk.yml        # build + testes + publicação em Releases
```
