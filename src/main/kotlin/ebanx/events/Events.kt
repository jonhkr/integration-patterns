package ebanx.events

import ebanx.logger
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.concurrent.thread

const val BOOTSTRAP_SERVERS = "192.168.100.28:9092"

fun createProducer(name: String): Producer<String, String> {
    val properties = Properties()
    properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = BOOTSTRAP_SERVERS
    properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
    properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
    properties[ProducerConfig.CLIENT_ID_CONFIG] = name

    return KafkaProducer(properties)
}

fun createConsumer(name: String): Consumer<String, String> {
    val properties = Properties()
    properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = BOOTSTRAP_SERVERS
    properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.canonicalName
    properties[ConsumerConfig.GROUP_ID_CONFIG] = name
    return KafkaConsumer(properties)
}

fun <K, V> runConsumer(consumer: Consumer<K, V>, fn: (ConsumerRecord<K, V>) -> Unit) {
    thread(start = true) {
        while (true) {
            consumer.poll(Duration.ofSeconds(1))
                .groupBy { "${it.topic()}:${it.partition()}" }
                .forEach { (_, records) ->
                    for (record in records) {
                        try {
                            fn(record)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Thread.sleep(1000)
                            consumer.seek(TopicPartition(record.topic(), record.partition()), record.offset())

                            break
                        }
                    }
                }
        }
    }
}

data class AccountDebited(val idempotencyKey: String, val accountId: Int, val amount: Int)

class Ledger {
    private val log = logger("ledger")
    private val producer = createProducer("ledger")
    private val consumer = createConsumer("ledger")

    init {
        consumer.subscribe(listOf("payments"))

        runConsumer(consumer) {
            if (it.value().startsWith("PaymentRequested")) {
                onPaymentRequested(PaymentRequested(it.key(), it.value().split(":")[1].toInt()))
            }
        }
    }

    private fun onPaymentRequested(event: PaymentRequested) {
        log.info("Handling event: {}", event)
        producer.send(ProducerRecord("payments", event.idempotencyKey, "AccountDebited:1:${event.amount}"))
    }
}

data class PayRequested(val idempotencyKey: String, val amount: Int)
data class PayConfirmed(val idempotencyKey: String, val amount: Int)

class PaymentProvider {
    private val log = logger("paymentProvider")
    private val producer = createProducer("paymentProvider")
    private val consumer = createConsumer("paymentProvider")

    init {
        consumer.subscribe(listOf("payments"))

        runConsumer(consumer) {
            if (it.value().startsWith("AccountDebited")) {
                val params = it.value().split(":")
                onAccountDebited(AccountDebited(it.key(), params[1].toInt(), params[2].toInt()))
            }

            if (it.value().startsWith("PayRequested")) {
                val params = it.value().split(":")
                onPayRequested(PayRequested(it.key(), params[1].toInt()))
            }
        }
    }

    private fun onPayRequested(event: PayRequested) {
        log.info("Handling event: {}", event)
        producer.send(ProducerRecord("payments", event.idempotencyKey, "PayConfirmed:${event.amount}"))
    }

    private fun onAccountDebited(event: AccountDebited) {
        log.info("Handling event: {}", event)
        producer.send(ProducerRecord("payments", event.idempotencyKey, "PayRequested:${event.amount}"))
    }
}

data class PaymentRequest(val idempotencyKey: String, val amount: Int)
data class PaymentRequested(val idempotencyKey: String, val amount: Int)

class PaymentService {
    private val log = logger("paymentService")
    private val producer = createProducer("paymentService")
    private val consumer = createConsumer("paymentService")

    init {
        consumer.subscribe(listOf("payments"))

        runConsumer(consumer) {
            if (it.value().startsWith("PayConfirmed")) {
                println(it.offset())
                val params = it.value().split(":")
                onPayConfirmed(PayConfirmed(it.key(), params[1].toInt()))
            }
        }
    }

    private fun onPayConfirmed(event: PayConfirmed) {
        // if (Math.random() >= 0.8) {
        //     throw RuntimeException("Failed")
        // }
        log.info("Handling event: {}", event)
    }

    fun createPayment(request: PaymentRequest) {
        producer.send(ProducerRecord("payments", request.idempotencyKey, "PaymentRequested:${request.amount}"))
    }
}

fun main() {
    Ledger()
    PaymentProvider()
    val service = PaymentService()

    while (true) {
        val key = UUID.randomUUID().toString()
        service.createPayment(PaymentRequest(key, 100))

        Thread.sleep(1000)
    }
}