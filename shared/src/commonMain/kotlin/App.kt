import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

val guessableRepository: GuessableRepository = object : GuessableRepository {
    override suspend fun getAll(): List<Guessable> = listOf(
        Guessable("Donald Trump"),
    )

    override suspend fun add(guessable: Guessable) { }

    override suspend fun getRandom(): Guessable = getAll().first()
}

@Composable
fun App() {
    MaterialTheme {
        var currentState: ApplicationState by remember { mutableStateOf(ApplicationState.MainMenu) }
        val scope = rememberCoroutineScope()
        when (val state = currentState) {
            ApplicationState.MainMenu -> MainMenu(onPlayClick = {
                scope.launch {
                    currentState = ApplicationState.Playing(guessableRepository.getRandom())
                }
            })
            is ApplicationState.Playing -> PlayingScreen(
                state.guessable,
                OpenAIBasedGuessWhoClient().startGuessWhoSession(state.guessable.name)
            )
        }
    }
}

@Composable
fun MessageInput(onMessageSend: (String) -> Unit) {
    var inputValue by remember { mutableStateOf("") }

    fun sendMessageAndDelete() {
        onMessageSend(inputValue)
        inputValue = ""
    }

    Row {
        TextField(
            modifier = Modifier.weight(1f),
            value = inputValue,
            onValueChange = { inputValue = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions { sendMessageAndDelete() },
        )
        Button(
            modifier = Modifier.height(56.dp),
            onClick = { sendMessageAndDelete() },
            enabled = inputValue.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send!"
            )
        }
    }
}

@Composable
fun PlayingScreen(guessable: Guessable, guessWhoSession: GuessWhoSession) {
    var firstMessage: Message? by remember { mutableStateOf(null) }

    LaunchedEffect(guessWhoSession) {
        guessWhoSession.sendChat(
            """
            We are playing Guess Who. You are now ${guessable.name}. Talk and act exactly like you are, 
            but do NOT say exactly who you are!
            Once I have successfully guessed your name. Start your message with "SUCCESS:" and thank me for playing while maintaining your role.
        """.trimIndent()
        ).also { firstMessage = Message.Answer.Loading(it) }
    }

    firstMessage
        ?.let { PlayingScreenLoaded(guessWhoSession, it) }
        ?: PlayingScreenLoading()
}

@Composable
fun PlayingScreenLoading() {
    Text("Loading...")
}

@Composable
fun PlayingScreenLoaded(session: GuessWhoSession, initialMessage: Message) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf(initialMessage)) }
    var job: Job? = null
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MessageList(messages)
        MessageInput(onMessageSend = { sentText ->
            job?.cancel()
            job = scope.launch {
                messages = messages + Message.Question(sentText)
                session.sendChat(sentText)
                    .also {
                        messages = messages + Message.Answer.Loading(it)
                    }
            }
        })
    }
}

@Composable
fun MessageList(messages: List<Message>) {
    Box(
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(fraction = .8f),
        ) {
            items(messages) { message ->
                MessageBalloon(message)
            }
        }
    }
}

// Heavily inspired from https://getstream.io/blog/android-jetpack-compose-chat-example/
@Composable
fun MessageBalloon(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = when (message) {
            is Message.Answer -> Alignment.Start
            is Message.Question -> Alignment.End
        },
    ) {
        Card(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = cardShapeFor(message),
            backgroundColor =
            when (message) {
                is Message.Answer -> MaterialTheme.colors.secondary
                is Message.Question -> MaterialTheme.colors.primary
            },
        ) {
            MessageText(message)
        }
        Text(
            text = when (message) {
                is Message.Answer -> "X"
                is Message.Question -> "You"
            },
            fontSize = 12.sp,
        )
    }
}

@Composable
fun MessageText(message: Message) {
    var text by remember { mutableStateOf("") }

    when (message) {
        is Message.FinishedMessage -> {
            text = message.text
        }
        is Message.Answer.Loading -> {
            LaunchedEffect(message) {
                message.chatStream.response
                    .groupTimed(60.milliseconds)
                    .collect {
                        text += it.joinToString(separator = " ")
                    }
            }
        }
    }

    Text(
        modifier = Modifier.padding(8.dp),
        text = text,
        color = when (message) {
            is Message.Answer -> MaterialTheme.colors.onSecondary
            is Message.Question -> MaterialTheme.colors.onPrimary
        },
    )
}

@Composable
fun cardShapeFor(message: Message): Shape =
    RoundedCornerShape(16.dp).let {
        when (message) {
            is Message.Answer -> it.copy(bottomStart = CornerSize(0))
            is Message.Question -> it.copy(bottomEnd = CornerSize(0))
        }
    }

sealed interface Message {
    sealed interface FinishedMessage: Message {
        val text: String
    }
    data class Question(override val text: String): FinishedMessage
    sealed class Answer: Message {
        data class Done(override val text: String): Answer(), FinishedMessage
        data class Loading(val chatStream: ChatStream): Answer()
    }
}

sealed class ApplicationState {
    data object MainMenu: ApplicationState()
    data class Playing(val guessable: Guessable): ApplicationState()
}

expect fun getPlatformName(): String