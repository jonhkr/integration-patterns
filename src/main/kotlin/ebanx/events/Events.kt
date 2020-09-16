package ebanx.events

import ebanx.logger
import java.util.UUID

typealias EventSubscriber = (Any) -> Unit

object Events {
    private val log = logger("broker")
    private val subscribers = mutableListOf<(EventSubscriber)>()
    fun subscribe(fn: EventSubscriber) {
        log.info("Registering subscriber {}", fn)
        subscribers.add(fn)
    }

    fun publish(event: Any) {
        log.info("Publishing event {}", event)
        subscribers.forEach { it(event) }
    }
}

data class AccountDebited(val idempotencyKey: String, val accountId: Int, val amount: Int)

class Ledger {
    private val log = logger("ledger")
    init {
        Events.subscribe {
            when (it) {
                is PaymentRequested -> onPaymentRequested(it)
            }
        }
    }

    private fun onPaymentRequested(event: PaymentRequested) {
        log.info("Handling event: {}", event)
        Events.publish(AccountDebited(event.idempotencyKey, 1, event.amount))
    }
}

data class PayRequested(val idempotencyKey: String, val amount: Int)
data class PayConfirmed(val idempotencyKey: String, val amount: Int)

class PaymentProvider {
    private val log = logger("paymentProvider")
    init {
        Events.subscribe {
            when (it) {
                is AccountDebited -> onAccountDebited(it)
                is PayRequested -> onPayRequested(it)
            }
        }
    }

    private fun onPayRequested(event: PayRequested) {
        log.info("Handling event: {}", event)
        Events.publish(PayConfirmed(event.idempotencyKey, event.amount))
    }

    private fun onAccountDebited(event: AccountDebited) {
        log.info("Handling event: {}", event)
        Events.publish(PayRequested(event.idempotencyKey, event.amount))
    }
}

data class PaymentRequest(val idempotencyKey: String, val amount: Int)
data class PaymentRequested(val idempotencyKey: String, val amount: Int)

class PaymentService {
    private val log = logger("paymentService")
    init {
        Events.subscribe {
            when (it) {
                is PayConfirmed -> log.info("Handling event: {}", it)
            }
        }
    }

    fun createPayment(request: PaymentRequest) {
        Events.publish(PaymentRequested(request.idempotencyKey, request.amount))
    }
}

fun main() {
    Ledger()
    PaymentProvider()
    val service = PaymentService()

    val key = UUID.randomUUID().toString()
    service.createPayment(PaymentRequest(key, 100))
}