package chat.rocket.core.internal.rest

import chat.rocket.common.model.Token
import chat.rocket.common.util.PlatformLogger
import chat.rocket.core.RocketChatClient
import chat.rocket.core.TokenRepository
import io.fabric8.mockwebserver.DefaultMockServer
import kotlinx.coroutines.experimental.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.hamcrest.CoreMatchers.`is` as isEqualTo

class MessagesTest {

    private lateinit var mockServer: DefaultMockServer

    private lateinit var sut: RocketChatClient

    @Mock
    private lateinit var tokenProvider: TokenRepository

    private val authToken = Token("userId", "authToken")

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        mockServer = DefaultMockServer()
        mockServer.start()

        val baseUrl = HttpUrl.parse(mockServer.url("/"))
        val client = OkHttpClient()
        sut = RocketChatClient.create {
            httpClient = client
            restUrl = baseUrl!!
            websocketUrl = "not needed"
            tokenRepository = this@MessagesTest.tokenProvider
            platformLogger = PlatformLogger.NoOpLogger()
        }

        Mockito.`when`(tokenProvider.get()).thenReturn(authToken)
    }

    @Test
    fun `sendMessage() should return a complete Message object`() {
        mockServer.expect()
                .post()
                .withPath("/api/v1/chat.sendMessage")
                .andReturn(200, SEND_MESSAGE_OK)
                .once()

        runBlocking {
            val msg= sut.sendMessage(
                    id = "messageId",
                    roomId = "GENERAL",
                    message = "Sending message from SDK to #general and @here")

            with(msg) {
                assertThat(senderAlias, isEqualTo(nullValue()))
                assertThat(message, isEqualTo("Sending message from SDK to #general and @here with url https://github.com/RocketChat/Rocket.Chat.Kotlin.SDK/"))
                assertThat(parseUrls, isEqualTo(false))
                assertThat(groupable, isEqualTo(false))
                assertThat(avatar, isEqualTo(nullValue()))
                assertThat(timestamp, isEqualTo(1513027870537L))

                with(sender!!) {
                    assertThat(id, isEqualTo("userId"))
                    assertThat(name, isEqualTo("testuser"))
                    assertThat(username, isEqualTo("testuser"))
                }
                assertThat(roomId, isEqualTo("GENERAL"))

                assertThat(urls!!.size, isEqualTo(1))
                with(urls!![0]) {
                    assertThat(url, isEqualTo("https://github.com/RocketChat/Rocket.Chat.Kotlin.SDK/"))
                }

                assertThat(mentions!!.size, isEqualTo(1))
                with(mentions!![0]) {
                    assertThat(id, isEqualTo("here"))
                    assertThat(username, isEqualTo("here"))
                }

                assertThat(channels!!.size, isEqualTo(1))
                with(channels!![0]) {
                    assertThat(id, isEqualTo("GENERAL"))
                    assertThat(name, isEqualTo("general"))
                }

                assertThat(updatedAt, isEqualTo(1513632670538L))
                assertThat(id, isEqualTo("messageId"))
            }
        }
    }

    @After
    fun shutdown() {
        mockServer.shutdown()
    }
}