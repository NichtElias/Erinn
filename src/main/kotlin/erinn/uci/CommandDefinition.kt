package party.elias.erinn.uci

import java.util.function.Consumer

enum class CommandDefinition(val handler: Consumer<Command>) {
    UCI(UCIEngine::handleUci),
    DEBUG(UCIEngine::handleDebug),
    QUIT(UCIEngine::handleQuit),
    SETOPTION(UCIEngine::handleSetOption),
    ISREADY(UCIEngine::handleIsReady),
    UCINEWGAME(UCIEngine::handleUciNewGame),
    POSITION(UCIEngine::handlePosition),
    GO(UCIEngine::handleGo),
    STOP(UCIEngine::handleStop),
    SHOW(UCIEngine::handleShow),
    EVAL(UCIEngine::handleEval),
    GENPOS(UCIEngine::handleGenPos)
}