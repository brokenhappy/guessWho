import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

fun <T> Flow<T>.groupTimed(n: Duration): Flow<List<T>> = channelFlow {
    coroutineScope {
        val buffer = mutableListOf<T>()
        var waiting = false
        val job = launch {
            while (true) {
                delay(n)
                waiting = if (buffer.isEmpty()) {
                    true
                } else {
                    channel.send(buffer.toList())
                    buffer.clear()
                    false
                }
            }
        }

        collect { newValue ->
            if (waiting) {
                channel.send(listOf(newValue))
                waiting = false
            } else {
                buffer.add(newValue)
            }
        }
        if (buffer.isNotEmpty()) channel.send(buffer)
        job.cancel()
    }
}