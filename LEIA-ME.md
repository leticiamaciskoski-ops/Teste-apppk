# Server Hop (v0.5)

App Android com duas ferramentas para testar um jogo do Roblox (padrão: Steal An Egg), no mesmo app e no mesmo painel flutuante:

1. **Server hop**: troca de servidor pela lista pública do Roblox. Tipos *Vazio* (1-2 jogadores) e *Movimentado* (quase cheio). Não existe filtro de "iniciantes" (a API não informa nível/idade). Servidores com status desconhecido, fps < 30 ou ping > 500 ms nunca são escolhidos.
2. **Pesca automática**: a cada ciclo, **pega a vara** (toques no slot do inventário), segura para lançar, espera o sinal (lendo a cor de um pixel) e dá os toques rápidos. Usa a acessibilidade. A caminhada/rota da v0.4 foi removida.

Também: painel flutuante com abas Hop/Pesca (bolha recolhível) e abertura do Roblox ao lado em tela dividida.

Uso da pesca: ative a acessibilidade; abra o painel; aba Pesca: Calibrar pesca (slot da vara, segurar, toques, sinal), Testar, Iniciar. Ajustes na aba Pesca do app.

Atenção: tocar no slot de uma ferramenta que já está na mão a guarda; ajuste toques/ciclos conforme o seu jogo.

Mesmo pacote e mesma chave: instala por cima. Não testado em aparelho real.
