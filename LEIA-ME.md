# Server Hop (v0.4)

App Android com duas ferramentas para testar um jogo do Roblox (padrão: Steal An Egg), tudo no mesmo app e no mesmo painel flutuante:

1. **Server hop**: troca de servidor pela lista pública do Roblox. Tipos *Vazio* (1-2 jogadores) e *Movimentado* (quase cheio). Não existe filtro de "iniciantes" (a API não informa nível/idade). Servidores com status desconhecido, fps < 30 ou ping > 500 ms nunca são escolhidos.
2. **Pesca automática com rota**: anda até a poça (joystick), pesca por um tempo (padrão 5 min 30), anda para sair e anda de novo até a poça, em repetição. Usa a acessibilidade (gestos + leitura de cor de um pixel).

Também: painel flutuante com abas Hop/Pesca (bolha recolhível) e abertura do Roblox ao lado em tela dividida.

Uso da pesca: ative a acessibilidade; abra o painel; aba Pesca: Calibrar pesca, Calibrar joystick, Testar, Iniciar. Ajustes de tempos e direções na aba Pesca do app.

Mesmo pacote e mesma chave das versões anteriores: instala por cima. Não testado em aparelho real: entrada no servidor exato pelo link, joystick/caminhada em jogo, painel sobre o Roblox e tela dividida.
