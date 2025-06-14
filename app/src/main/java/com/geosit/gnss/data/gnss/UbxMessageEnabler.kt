package com.geosit.gnss.data.gnss

import com.geosit.gnss.ui.screens.connection.ConnectionManager
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.delay

/**
 * Helper class to configure u-blox receivers to output specific UBX messages
 */
class UbxMessageEnabler @Inject constructor(
    private val connectionManager: ConnectionManager
) {
    
    companion object {
        // Message Class and ID constants
        const val CLASS_NAV = 0x01
        const val CLASS_RXM = 0x02
        const val CLASS_INF = 0x04
        const val CLASS_CFG = 0x06
        const val CLASS_MON = 0x0A
        
        // NAV messages
        const val NAV_POSECEF = 0x01
        const val NAV_POSLLH = 0x02
        const val NAV_STATUS = 0x03
        const val NAV_DOP = 0x04
        const val NAV_SOL = 0x06
        const val NAV_PVT = 0x07
        const val NAV_VELECEF = 0x11
        const val NAV_VELNED = 0x12
        const val NAV_HPPOSECEF = 0x13
        const val NAV_HPPOSLLH = 0x14
        const val NAV_TIMEGPS = 0x20
        const val NAV_TIMEUTC = 0x21
        const val NAV_CLOCK = 0x22
        const val NAV_SAT = 0x35
        const val NAV_SVIN = 0x3B
        const val NAV_RELPOSNED = 0x3C
        const val NAV_SIG = 0x43
        
        // RXM messages
        const val RXM_RAWX = 0x15
        const val RXM_SFRBX = 0x13
        const val RXM_MEASX = 0x14
        const val RXM_RTCM = 0x32
        
        // MON messages
        const val MON_VER = 0x04
        const val MON_HW = 0x09
        const val MON_GNSS = 0x28
        const val MON_COMMS = 0x36
        const val MON_RF = 0x38
    }
    
    data class MessageConfig(
        val msgClass: Int,
        val msgId: Int,
        val rate: Int = 1,  // 1 = ogni epoca, 0 = disabilitato
        val description: String
    )
    
    /**
     * Messaggi essenziali per operazioni GNSS standard
     */
    val essentialMessages = listOf(
        MessageConfig(CLASS_NAV, NAV_PVT, 1, "Navigation PVT"),
        MessageConfig(CLASS_NAV, NAV_STATUS, 1, "Navigation Status"),
        MessageConfig(CLASS_NAV, NAV_SAT, 1, "Satellite Information"),
        MessageConfig(CLASS_NAV, NAV_DOP, 1, "Dilution of Precision"),
        MessageConfig(CLASS_NAV, NAV_SIG, 1, "Signal Information")
    )
    
    /**
     * Messaggi per alta precisione (RTK/PPP)
     */
    val highPrecisionMessages = listOf(
        MessageConfig(CLASS_NAV, NAV_HPPOSECEF, 1, "High Precision ECEF"),
        MessageConfig(CLASS_NAV, NAV_HPPOSLLH, 1, "High Precision LLH"),
        MessageConfig(CLASS_NAV, NAV_RELPOSNED, 1, "Relative Position NED"),
        MessageConfig(CLASS_NAV, NAV_SVIN, 1, "Survey-in Status"),
        MessageConfig(CLASS_RXM, RXM_RTCM, 1, "RTCM Input Status")
    )
    
    /**
     * Messaggi per post-processing
     */
    val rawDataMessages = listOf(
        MessageConfig(CLASS_RXM, RXM_RAWX, 1, "Raw Measurements"),
        MessageConfig(CLASS_RXM, RXM_SFRBX, 1, "Subframe Buffer"),
        MessageConfig(CLASS_RXM, RXM_MEASX, 1, "Measurements")
    )
    
    /**
     * Messaggi di monitoraggio
     */
    val monitoringMessages = listOf(
        MessageConfig(CLASS_MON, MON_VER, 0, "Version (one time)"),
        MessageConfig(CLASS_MON, MON_HW, 5, "Hardware Status"),
        MessageConfig(CLASS_MON, MON_GNSS, 5, "GNSS Status"),
        MessageConfig(CLASS_MON, MON_RF, 10, "RF Information")
    )
    
    /**
     * Abilita un set di messaggi sul ricevitore
     */
    suspend fun enableMessageSet(messages: List<MessageConfig>) {
        messages.forEach { msg ->
            enableMessage(msg)
            delay(50) // Piccola pausa tra i comandi
        }
    }
    
    /**
     * Abilita un singolo messaggio
     */
    fun enableMessage(config: MessageConfig) {
        // CFG-MSG payload: class(1) + id(1) + rate for each port
        // Assumiamo 6 porte: DDC/I2C, UART1, UART2, USB, SPI, reserved
        val payload = ByteArray(8)
        payload[0] = config.msgClass.toByte()
        payload[1] = config.msgId.toByte()
        
        // Abilita solo su USB (porta 3) e UART1 (porta 1)
        payload[2] = 0 // DDC/I2C
        payload[3] = if (config.rate > 0) config.rate.toByte() else 0 // UART1
        payload[4] = 0 // UART2
        payload[5] = if (config.rate > 0) config.rate.toByte() else 0 // USB
        payload[6] = 0 // SPI
        payload[7] = 0 // Reserved
        
        connectionManager.sendUbxCommand(0x06, 0x01, payload)
        
        Timber.d("Enabling UBX message: ${config.description} (${String.format("%02X-%02X", config.msgClass, config.msgId)}) at rate ${config.rate}")
    }
    
    /**
     * Disabilita tutti i messaggi NMEA
     */
    suspend fun disableAllNmea() {
        val nmeaMessages = listOf(
            0xF0 to 0x00, // GGA
            0xF0 to 0x01, // GLL
            0xF0 to 0x02, // GSA
            0xF0 to 0x03, // GSV
            0xF0 to 0x04, // RMC
            0xF0 to 0x05, // VTG
            0xF0 to 0x06, // GRS
            0xF0 to 0x07, // GST
            0xF0 to 0x08, // ZDA
            0xF0 to 0x09, // GBS
            0xF0 to 0x0A, // DTM
            0xF0 to 0x0D, // GNS
            0xF0 to 0x0E, // THS
            0xF0 to 0x0F  // VLW
        )
        
        nmeaMessages.forEach { (msgClass, msgId) ->
            val config = MessageConfig(msgClass, msgId, 0, "NMEA")
            enableMessage(config)
            delay(30) // Piccola pausa tra i comandi
        }
        
        Timber.d("Disabled all NMEA messages")
    }
    
    /**
     * Configurazione consigliata per GeoSit
     */
    suspend fun configureForGeoSit(enableRawData: Boolean = false, enableHighPrecision: Boolean = false) {
        Timber.d("Configuring u-blox for GeoSit...")
        
        // 1. Disabilita NMEA
        disableAllNmea()
        
        // 2. Abilita messaggi essenziali
        enableMessageSet(essentialMessages)
        
        // 3. Abilita monitoraggio (a rate ridotto)
        enableMessageSet(monitoringMessages)
        
        // 4. Opzionalmente abilita alta precisione
        if (enableHighPrecision) {
            enableMessageSet(highPrecisionMessages)
        }
        
        // 5. Opzionalmente abilita raw data per post-processing
        if (enableRawData) {
            enableMessageSet(rawDataMessages)
        }
        
        // 6. Salva configurazione in flash (CFG-CFG)
        delay(500) // Aspetta che tutti i comandi siano processati
        saveConfiguration()
        
        Timber.d("u-blox configuration complete")
    }
    
    /**
     * Salva la configurazione corrente nella flash del ricevitore
     */
    private fun saveConfiguration() {
        // CFG-CFG: clearMask(4) + saveMask(4) + loadMask(4) + deviceMask(1)
        val payload = ByteArray(13)
        
        // Clear nothing
        payload[0] = 0x00
        payload[1] = 0x00
        payload[2] = 0x00
        payload[3] = 0x00
        
        // Save all sections
        payload[4] = 0x1F.toByte()
        payload[5] = 0x1F.toByte()
        payload[6] = 0x00
        payload[7] = 0x00
        
        // Load nothing
        payload[8] = 0x00
        payload[9] = 0x00
        payload[10] = 0x00
        payload[11] = 0x00
        
        // Device mask: BBR, Flash, EEPROM
        payload[12] = 0x17.toByte()
        
        connectionManager.sendUbxCommand(0x06, 0x09, payload)
        
        Timber.d("Configuration saved to receiver flash memory")
    }
    
    /**
     * Reset alle impostazioni di default
     */
    fun resetToDefaults() {
        // CFG-CFG con loadMask settato per caricare i default
        val payload = ByteArray(13)
        
        // Clear all
        payload[0] = 0xFF.toByte()
        payload[1] = 0xFF.toByte()
        payload[2] = 0x00
        payload[3] = 0x00
        
        // Save nothing
        payload[4] = 0x00
        payload[5] = 0x00
        payload[6] = 0x00
        payload[7] = 0x00
        
        // Load defaults
        payload[8] = 0xFF.toByte()
        payload[9] = 0xFF.toByte()
        payload[10] = 0x00
        payload[11] = 0x00
        
        // Device mask: BBR
        payload[12] = 0x01.toByte()
        
        connectionManager.sendUbxCommand(0x06, 0x09, payload)
        
        Timber.d("Reset to default configuration")
    }
    
    /**
     * Richiede la versione del firmware (MON-VER)
     */
    fun requestVersion() {
        // MON-VER non ha payload
        connectionManager.sendUbxCommand(0x0A, 0x04, byteArrayOf())
        Timber.d("Requested firmware version")
    }
    
    /**
     * Configura il rate di navigazione (in Hz)
     */
    fun setNavigationRate(rateHz: Int = 1) {
        // CFG-RATE: measRate(2) + navRate(2) + timeRef(2)
        val payload = ByteArray(6)
        
        val measRate = 1000 / rateHz // millisecondi tra le misure
        
        // Measurement rate in ms (little endian)
        payload[0] = (measRate and 0xFF).toByte()
        payload[1] = (measRate shr 8).toByte()
        
        // Navigation rate (cycles) - sempre 1
        payload[2] = 0x01
        payload[3] = 0x00
        
        // Time reference: 0 = UTC, 1 = GPS
        payload[4] = 0x01
        payload[5] = 0x00
        
        connectionManager.sendUbxCommand(0x06, 0x08, payload)
        
        Timber.d("Set navigation rate to $rateHz Hz")
    }
}
