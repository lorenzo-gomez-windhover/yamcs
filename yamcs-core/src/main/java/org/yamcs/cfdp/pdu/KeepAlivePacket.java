package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.FileDirective;
import org.yamcs.cfdp.CfdpUtils;

public class KeepAlivePacket extends CfdpPacket implements FileDirective {

    private long progress;

    public KeepAlivePacket(long progress, CfdpHeader header) {
        super(header);
        this.progress = progress;
    }

    public KeepAlivePacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.progress = CfdpUtils.getUnsignedInt(buffer);
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        CfdpUtils.writeUnsignedInt(buffer, progress);
    }

    @Override
    public int getDataFieldLength() {
        return 5;
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.KEEP_ALIVE;
    }

    @Override
    public String toString() {
        return "KeepAlivePacket [progress=" + progress + "]";
    }

}
