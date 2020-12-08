package fr.acinq.phoenix.app

import fr.acinq.bitcoin.Base58
import fr.acinq.bitcoin.Base58Check
import fr.acinq.bitcoin.Bech32
import fr.acinq.phoenix.data.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class Utilities(
    loggerFactory: LoggerFactory,
    private val chain: Chain
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    data class BitcoinAddressValidationResult(
        val myChain: Chain,
        val addrChain: Chain?
    ) {
        val isValid = myChain == addrChain
    }

    fun isValidBitcoinAddress(addr: String): BitcoinAddressValidationResult {

        try { // is Base58 ?
            val (prefix, _) = Base58Check.decode(addr)
            val addrChain = when (prefix) {
                Base58.Prefix.PubkeyAddress -> Chain.MAINNET
                Base58.Prefix.ScriptAddress -> Chain.MAINNET
                Base58.Prefix.SecretKey -> Chain.MAINNET
                Base58.Prefix.PubkeyAddressTestnet -> Chain.TESTNET
                Base58.Prefix.ScriptAddressTestnet -> Chain.TESTNET
                Base58.Prefix.SecretKeyTestnet -> Chain.TESTNET
                Base58.Prefix.PubkeyAddressSegnet -> Chain.REGTEST
                Base58.Prefix.ScriptAddressSegnet -> Chain.REGTEST
                Base58.Prefix.SecretKeySegnet -> Chain.REGTEST
                else -> null
            }

            return BitcoinAddressValidationResult(chain, addrChain)

        } catch (e: Throwable) {
            // Not Base58Check
        }

        try { // is Bech32 ?
            val (hrp, version, _) = Bech32.decodeWitnessAddress(addr)
            if (version != 0.toByte()) {
                // Unknown version - we don't have any validation logic in place for it
                return BitcoinAddressValidationResult(chain, null)
            }
            val addrChain = when (hrp) {
                "bc" -> Chain.MAINNET
                "tb" -> Chain.TESTNET
                "bcrt" -> Chain.REGTEST
                else -> null
            }

            return BitcoinAddressValidationResult(chain, addrChain)

        } catch (e: Throwable) {
            // Not Bech32
        }

        return BitcoinAddressValidationResult(chain, null)
    }
}