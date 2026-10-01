# Erinn
Erinn is a UCI chess engine written in Kotlin. It is built for version 21 of the JVM.

Version 1.1 is currently at ~2710 Elo on [CCRL](https://computerchess.org.uk/4040/cgi/engine_details.cgi?print=Details&each_game=0&eng=Erinn%201.1%2064-bit).

## How to Play

1. As Erinn is a UCI engine, you will need a GUI like [En Croissant](https://encroissant.org/) or [Cute Chess](https://cutechess.com/).
2. You can download a release version [here](https://github.com/NichtElias/Erinn/releases) or build it yourself by
cloning the repo and running `./gradlew shadowJar`.
3. Many GUIs only accept an executable file as am engine, so you may need to make a small shell script that runs `java -jar /path/to/Erinn-<version>.jar`.

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
- Late move pruning
- SEE pruning
- [Recapture extensions](https://chessprogramming.org/Recapture_Extensions)
- [Delta pruning](https://www.chessprogramming.org/Delta_Pruning) and SEE pruning in quiescence search

**Move Generation/Ordering**:

- Staged move generator
- Separate evasion move generator
- Fist move comes from TT if available
- Captures separated into good and bad by [static exchange evaluation](https://www.chessprogramming.org/Static_Exchange_Evaluation), then sorted by [MVV-LVA](https://www.chessprogramming.org/MVV-LVA)
- [Killer heuristic](https://www.chessprogramming.org/Killer_Heuristic)
- Remaining quiet moves ordered by [history heuristic](https://www.chessprogramming.org/History_Heuristic) including counter move history

**NNUE Evaluation**:

Positions are evaluated with [NNUE](https://www.chessprogramming.org/NNUE). The currently used model architecture is `(768x8 -> 128)x2 -> 1x8`, i.e.
8 horizontally mirrored king buckets, a hidden layer of 128 neurons and 8 output buckets. SCReLU is used as the
activation function for the hidden layer.

## Resources and Inspirations

- The [Chess Programming Wiki](https://www.chessprogramming.org)
- This [wonderful writeup about NNUE](https://official-stockfish.github.io/docs/nnue-pytorch-wiki/docs/nnue.html) by the stockfish devs
- This [reminder not to overcomplicate your NNUE architecture](https://github.com/jw1912/bullet/blob/main/docs/1-basics.md#beginner-traps)
- The [Stockfish source code](https://github.com/official-stockfish/Stockfish) as well as the code of many other open source engines like [Viridithas](https://github.com/cosmobobak/viridithas), [Stormphrax](https://github.com/Ciekce/Stormphrax) or [Calvin](https://github.com/kelseyde/calvin-chess-engine)
- The [Engine Programming Discord](https://discord.com/invite/F6W6mMsTGN)
- The [chess programming videos](https://www.youtube.com/playlist?list=PLFt_AvWsXl0cvHyu32ajwh2qU1i6hl77c) by Sebastian Lague
- Various other sources like open source engines and [talkchess/Computer Chess Club](https://talkchess.com/) forum posts

