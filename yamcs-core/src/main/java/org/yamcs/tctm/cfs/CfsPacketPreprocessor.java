package org.yamcs.tctm.cfs;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * Preprocessor for the CFS TM packets:
 * <ul>
 * <li>CCSDS primary header 6 bytes</li>
 * <li>Time seconds 4 bytes</li>
 * <li>subseconds(1/2^16 fraction of seconds) 2 bytes</li>
 * </ul>
 * 
 * Options:
 * <pre>
 *   dataLinks:
 *   ...
 *      packetPreprocessor: org.yamcs.tctm.cfs.CfsPacketPreprocessor
 *      packetPreprocessorArgs:
 *          byteOrder: LITTLE_ENDIAN
 *          timeEncoding:
 *              epoch: CUSTOM
 *              epochUTC: 1970-01-01T00:00:00Z
 *              timeIncludesLeapSeconds: false
 *   
 *  </pre>  
 * 
 * The {@code byteOrder} option (default is {@code BIG_ENDIAN}) is used only for decoding the timestamp in the secondary header: the 4 bytes second and 2 bytes
 * subseconds are decoded in little endian.
 * <p>
 * The primary CCSDS header is always decoded as BIG_ENDIAN.
 * <p>
 * The conversion of the extracted time to Yamcs time is based on the timeEncoding properties.
 * 
 * {@code epoch} can be one of TAI, J2000, UNIX, GPS, CUSTOM.
 * <p>
 * If CUSTOM is specified, the {@code epochUTC} has to be used to specify the UTC time which is used as an epoch (UTC is
 * used here loosely because strictly speaking UTC has been only introduced in 1972 so it does not make sense for the times before).
 * <p>
 * The time read from the packet is interpreted as delta from {@code epochUTC}.
 * <p>If {@code timeIncludesLeapSeconds} is {@code true} (default), the delta time is considered as having the leap seconds included
 * (practically it is the real time that passed).
 * <p>
 * TAI, J2000 and GPS have the leap seconds included, UNIX does not.
 * <p>
 * The example above is equivalent with:
 * <pre>
 * timeEncoding:
 *    epoch: UNIX
 * </pre>
 */
public class CfsPacketPreprocessor extends AbstractPacketPreprocessor {
    private Map<Integer, AtomicInteger> seqCounts = new HashMap<>();
    private final Log log;
    static final int MINIMUM_LENGTH = 12;
    private boolean checkForSequenceDiscontinuity = true;

    public CfsPacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public CfsPacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
        this.log = new Log(getClass(), yamcsInstance);
    }

    @Override
    public TmPacket process(TmPacket pwt) {
        byte[] packet = pwt.getPacket();
        if (packet.length < MINIMUM_LENGTH) {
            eventProducer.sendWarning("SHORT_PACKET",
                    "Short packet received, length: " + packet.length + "; minimum required length is " + MINIMUM_LENGTH
                            + " bytes.");
            return null;
        }
        int apidseqcount = ByteArrayUtils.decodeInt(packet, 0);
        int apid = (apidseqcount >> 16) & 0x07FF;
        int seq = (apidseqcount) & 0x3FFF;
        AtomicInteger ai = seqCounts.computeIfAbsent(apid, k -> new AtomicInteger());
        int oldseq = ai.getAndSet(seq);

        if (checkForSequenceDiscontinuity && ((seq - oldseq) & 0x3FFF) != 1) {
            eventProducer.sendWarning("SEQ_COUNT_JUMP",
                    "Sequence count jump for apid: " + apid + " old seq: " + oldseq + " newseq: " + seq);
        }
        if (useLocalGenerationTime) {
            pwt.setLocalGenTime();
            pwt.setGenerationTime(timeService.getMissionTime());
        } else {
            pwt.setGenerationTime(getTimeFromPacket(packet));
        }
        pwt.setSequenceCount(apidseqcount);
        if (log.isTraceEnabled()) {
            log.trace("processing packet apid: {}, seqCount:{}, length: {}, genTime: {}", apid, seq, packet.length,
                    TimeEncoding.toString(pwt.getGenerationTime()));
        }
        return pwt;
    }

    long getTimeFromPacket(byte[] packet) {
        long sec;
        int subsecs;

        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            sec = ByteArrayUtils.decodeInt(packet, 6) & 0xFFFFFFFFL;
            subsecs = ByteArrayUtils.decodeShort(packet, 10);
        } else {
            sec = ByteArrayUtils.decodeIntLE(packet, 6) & 0xFFFFFFFFL;
            subsecs = ByteArrayUtils.decodeShortLE(packet, 10);
        }
        return shiftFromEpoch(1000 * sec + subsecs * 1000 / 65536);
    }

    public boolean checkForSequenceDiscontinuity() {
        return checkForSequenceDiscontinuity;
    }

    @Override
    public void checkForSequenceDiscontinuity(boolean checkForSequenceDiscontinuity) {
        this.checkForSequenceDiscontinuity = checkForSequenceDiscontinuity;
    }

}
