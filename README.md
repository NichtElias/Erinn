# Erinn
Erinn is a UCI chess engine written in Kotlin. It is built for version 21 of the JVM.

## Features
**Search**:

- [Iterative deepening](https://www.chessprogramming.org/Iterative_Deepening)
- [Principal variation search](https://www.chessprogramming.org/Principal_Variation_Search)
- [Aspiration windows](https://www.chessprogramming.org/Aspiration_Windows)
- [Check extensions](https://www.chessprogramming.org/Check_Extensions)
- [Late move reductions](https://www.chessprogramming.org/Late_Move_Reductions)
- [Null move pruning](https://www.chessprogramming.org/Null_Move_Pruning)
- [Futility pruning](https://www.chessprogramming.org/Futility_Pruning)
- [Reverse futility pruning](https://www.chessprogramming.org/Reverse_Futility_Pruning)
- [Delta pruning](https://www.chessprogramming.org/Delta_Pruning) in quiescence search

**Move Generation/Ordering**:

- Staged move generator
- Separate evasion move generator
- First move comes from PV table if available
- Second move comes from TT if available
- Captures ordered by [static exchange evaluation](https://www.chessprogramming.org/Static_Exchange_Evaluation) for high depths, [MVV-LVA](https://www.chessprogramming.org/MVV-LVA) for low depths
- [Killer heuristic](https://www.chessprogramming.org/Killer_Heuristic)
- Remaining quiet moves ordered by [relative history heuristic](https://www.chessprogramming.org/Relative_History_Heuristic)

**NNUE Evaluation**:

Positions are evaluated with [NNUE](https://www.chessprogramming.org/NNUE). For Erinn the goal is to use a relatively small net for simplicity and due to the
limitations of vectorization in the JVM. The currently used model architecture is `49152*2 -> 64+8 -> 1`.

A halfKA input feature set is used for the net to make the most out of the small hidden layer and minimal layer count.

## Resources and Inspirations

- The [Chess Programming Wiki](https://www.chessprogramming.org)
- This [wonderful writeup about NNUE](https://official-stockfish.github.io/docs/nnue-pytorch-wiki/docs/nnue.html) by the stockfish devs
- The [Stockfish source code](https://github.com/official-stockfish/Stockfish)
- The [chess programming videos](https://www.youtube.com/playlist?list=PLFt_AvWsXl0cvHyu32ajwh2qU1i6hl77c) by Sebastian Lague
- Various other sources like open source engines and [talkchess/Computer Chess Club](https://talkchess.com/) forum posts

