package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.CertificateType
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.X509Utilities
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Very basic flow which requests a transaction key from a counterparty, intended for use as a subflow of another
 * flow.
 */
object TxKeyFlow {
    @StartableByRPC
    @InitiatingFlow
    class Requester(val otherSide: Party,
                    override val progressTracker: ProgressTracker) : FlowLogic<AnonymousIdentity>() {
        constructor(otherSide: Party) : this(otherSide, tracker())

        companion object {
            object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

            fun tracker() = ProgressTracker(AWAITING_KEY)
        }

        @Suspendable
        override fun call(): AnonymousIdentity {
            progressTracker.currentStep = AWAITING_KEY
            // TODO: Don't trust self-signed certificates
            val untrustedKey = receive<Pair<X509Certificate, CertPath>>(otherSide)
            return untrustedKey.unwrap { (wellKnownCert, certPath) ->
                val theirCert = certPath.certificates.last()
                if (theirCert is X509Certificate) {
                    val certName = X500Name(theirCert.subjectDN.name)
                    if (certName == otherSide.name) {
                        val anonymousParty = AnonymousParty(theirCert.publicKey)
                        serviceHub.identityService.registerPath(wellKnownCert, anonymousParty, certPath)
                        AnonymousIdentity(certPath, theirCert, anonymousParty)
                    } else
                        throw IllegalStateException("Expected certificate subject to be ${otherSide.name} but found ${certName}")
                } else
                    throw IllegalStateException("Expected an X.509 certificate but received ${theirCert.javaClass.name}")
            }
        }
    }

    /**
     * Flow which waits for a key request from a counterparty, generates a new key and then returns it to the
     * counterparty and as the result from the flow.
     */
    class Provider(val otherSide: Party,
                   val revocationEnabled: Boolean,
                   override val progressTracker: ProgressTracker) : FlowLogic<CertPath>() {
        constructor(otherSide: Party, revocationEnabled: Boolean) : this(otherSide, revocationEnabled, tracker())

        companion object {
            object SENDING_KEY : ProgressTracker.Step("Sending key")

            fun tracker() = ProgressTracker(SENDING_KEY)
        }

        @Suspendable
        override fun call(): CertPath {
            progressTracker.currentStep = SENDING_KEY
            val ourPublicKey = serviceHub.keyManagementService.freshKey()
            val ourParty = Party(serviceHub.myInfo.legalIdentity.name, ourPublicKey)
            // FIXME: Use the actual certificate for the identity the flow is presenting themselves as
            // FIXME: Generate EdDSA keys and non-TLS certs
            val issuerKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val selfSignedCertificate = X509Utilities.createSelfSignedCACertificate(ourParty.name, issuerKey)
            val ourCertificate = X509Utilities.createCertificate(CertificateType.TLS, selfSignedCertificate, issuerKey, ourParty.name, ourPublicKey)
            val ourCertPath = X509Utilities.createCertificatePath(selfSignedCertificate, ourCertificate, revocationEnabled = revocationEnabled)
            serviceHub.identityService.registerPath(selfSignedCertificate,
                    AnonymousParty(ourParty.owningKey),
                    ourCertPath)
            // TODO: We shouldn't have to send the CA certificate, because it should be our well known identity.
            send(otherSide, Pair(selfSignedCertificate, ourCertPath))
            return ourCertPath
        }
    }

    data class AnonymousIdentity(
            val certPath: CertPath,
            val certificate: X509Certificate,
            val identity: AnonymousParty
    )
}
