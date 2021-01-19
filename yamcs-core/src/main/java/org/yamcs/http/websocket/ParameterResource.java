package org.yamcs.http.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.ParameterSubscriptionRequest;
import org.yamcs.protobuf.ParameterSubscriptionResponse;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;

/**
 * Provides realtime parameter subscription via web.
 *
 * @author nm
 *
 */
public class ParameterResource implements WebSocketResource, ParameterWithIdConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParameterResource.class);

    private ConnectedWebSocketClient client;

    private int firstSubscriptionId = -1;
    private int allSubscriptionId = -1;

    private ParameterWithIdRequestHelper pidrm;

    public ParameterResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null) {
            pidrm = new ParameterWithIdRequestHelper(processor.getParameterRequestManager(), this);
        }
    }

    @Override
    public String getName() {
        return "parameter";
    }

    private int getSubscriptionId(ParameterSubscriptionRequest req) {
        if (!req.hasSubscriptionId() || req.getSubscriptionId() == 0) {
            return firstSubscriptionId;
        } else {
            return req.getSubscriptionId();
        }
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ParameterSubscriptionRequest req;
        req = decoder.decodeMessageData(ctx, ParameterSubscriptionRequest.newBuilder()).build();
        int subscriptionId = getSubscriptionId(req);
        List<NamedObjectId> idList = req.getIdList();

        try {
            WebSocketReply reply = new WebSocketReply(ctx.getRequestId());
            try {
                if (pidrm == null) {
                    throw new WebSocketException(ctx.getRequestId(),
                            "Cannot subscribe without a parameter request manager");
                } else if (subscriptionId != -1) {
                    pidrm.addItemsToRequest(subscriptionId, idList, client.getUser());
                } else {
                    subscriptionId = pidrm.addRequest(idList, req.getUpdateOnExpiration(), client.getUser());
                    if (firstSubscriptionId == -1) {
                        firstSubscriptionId = subscriptionId;
                    }
                }
                ParameterSubscriptionResponse psr = getResponse(subscriptionId, idList, null);

                reply.attachData("ParameterSubscriptionResponse", psr);
            } catch (InvalidIdentification e) {
                NamedObjectList invalidList = NamedObjectList.newBuilder().addAllList(e.getInvalidParameters()).build();

                if (!req.hasAbortOnInvalid() || req.getAbortOnInvalid()) {
                    WebSocketException ex = new WebSocketException(ctx.getRequestId(), e);
                    ex.attachData("InvalidIdentification", invalidList);
                    throw ex;
                } else {
                    if (idList.size() == e.getInvalidParameters().size()) {
                        log.warn("Received subscribe attempt will all-invalid parameters");
                        idList = Collections.emptyList();
                    } else {
                        Set<NamedObjectId> valid = new HashSet<>(idList);
                        valid.removeAll(e.getInvalidParameters());
                        idList = new ArrayList<>(valid);

                        log.warn(
                                "Received subscribe attempt with {} invalid parameters. Subscription will continue with {} remaining valids.",
                                e.getInvalidParameters().size(), idList.size());
                        if (log.isDebugEnabled()) {
                            log.debug("The invalid IDs are: {}",
                                    StringConverter.idListToString(e.getInvalidParameters()));
                        }
                        if (subscriptionId != -1) {
                            pidrm.addItemsToRequest(subscriptionId, idList, client.getUser());
                        } else {
                            subscriptionId = pidrm.addRequest(idList, req.getUpdateOnExpiration(), client.getUser());
                        }
                        if (firstSubscriptionId == -1) {
                            firstSubscriptionId = subscriptionId;
                        }
                    }

                    ParameterSubscriptionResponse psr = getResponse(subscriptionId, idList, e.getInvalidParameters());
                    reply.attachData(ParameterSubscriptionResponse.class.getSimpleName(), psr);
                }
            }
            client.sendReply(reply);
            if ((!req.hasSendFromCache() || req.getSendFromCache())) {
                List<ParameterValueWithId> pvlist = pidrm.getValuesFromCache(subscriptionId);
                if (!pvlist.isEmpty()) {
                    update(subscriptionId, pvlist);
                }
            }

            return null;
        } catch (InvalidIdentification e) {
            log.warn("got invalid identification: {}", e.getMessage());
            throw new WebSocketException(ctx.getRequestId(), "internal error: " + e.toString(), e);
        } catch (InvalidRequestIdentification e) {
            log.warn("got invalid subscription id for {}", subscriptionId);
            throw new WebSocketException(ctx.getRequestId(), "invalid subscription id " + e.getSubscriptionId());
        } catch (NoPermissionException e) {
            log.warn("no permission for parameters: {}", e.getMessage());
            throw new WebSocketException(ctx.getRequestId(), "internal error: " + e.toString(), e);
        }
    }

    private ParameterSubscriptionResponse getResponse(int subscriptionId, List<NamedObjectId> validList,
            List<NamedObjectId> invalidList) {
        ParameterSubscriptionResponse.Builder psr = ParameterSubscriptionResponse.newBuilder()
                .setSubscriptionId(subscriptionId);

        if (invalidList != null) {
            psr.addAllInvalid(invalidList);
        }

        return psr.build();
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ParameterSubscriptionRequest req;
        req = decoder.decodeMessageData(ctx, ParameterSubscriptionRequest.newBuilder()).build();
        int subscriptionId = getSubscriptionId(req);

        List<NamedObjectId> paraList = req.getIdList();
        if (pidrm == null) {
            throw new WebSocketException(ctx.getRequestId(),
                    "Cannot unsubscribe without a parameter request manager");
        } else if (subscriptionId != -1) {
            try {
                pidrm.removeItemsFromRequest(subscriptionId, paraList, client.getUser());
            } catch (NoPermissionException e) {
                throw new WebSocketException(ctx.getRequestId(), "No permission", e);
            }
            return WebSocketReply.ack(ctx.getRequestId());
        } else {
            throw new WebSocketException(ctx.getRequestId(), "Not subscribed to anything");
        }
    }

    @Override
    public WebSocketReply subscribeAll(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (allSubscriptionId != -1) {
            throw new WebSocketException(ctx.getRequestId(), "Already subscribed for this client");
        }
        try {
            allSubscriptionId = pidrm.subscribeAll(client.getUser());
        } catch (NoPermissionException e) {
            throw new WebSocketException(ctx.getRequestId(), "No permission", e);
        }
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public WebSocketReply unsubscribeAll(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        ParameterSubscriptionRequest req;
        req = decoder.decodeMessageData(ctx, ParameterSubscriptionRequest.newBuilder()).build();
        int subscriptionId = getSubscriptionId(req);

        if ((allSubscriptionId == -1) && (subscriptionId == -1)) {
            throw new WebSocketException(ctx.getRequestId(), "Not subscribed");
        }
        ParameterRequestManager prm = pidrm.getPrm();
        if (allSubscriptionId != -1) {
            prm.unsubscribeAll(subscriptionId);
        }

        if (subscriptionId != -1) {
            pidrm.removeRequest(subscriptionId);
            if (subscriptionId == firstSubscriptionId) {
                firstSubscriptionId = -1;
            }
        }

        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void update(int subscriptionId, List<ParameterValueWithId> paramList) {
        if (paramList == null || paramList.isEmpty()) {
            return;
        }
        ParameterData.Builder pd = ParameterData.newBuilder()
                .setSubscriptionId(subscriptionId);
        for (ParameterValueWithId pvwi : paramList) {
            ParameterValue pv = pvwi.getParameterValue();
            pd.addParameter(pv.toGpb(pvwi.getId()));
        }
        client.sendData(ProtoDataType.PARAMETER, pd.build());
    }

    @Override
    public void unselectProcessor() {
        if (pidrm != null) {
            pidrm.unselectPrm();
        }
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (pidrm == null) {
            pidrm = new ParameterWithIdRequestHelper(processor.getParameterRequestManager(), this);
        }
        try {
            List<NamedObjectId> invalid = pidrm.selectPrm(processor.getParameterRequestManager(), client.getUser());
            if (!invalid.isEmpty()) {
                // send notification for invalid parameters
                ParameterData.Builder pd = ParameterData.newBuilder();
                long now = processor.getCurrentTime();
                for (NamedObjectId id : invalid) {
                    pd.addParameter(org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder().setId(id)
                            .setAcquisitionTime(TimeEncoding.toProtobufTimestamp(now))
                            .setAcquisitionStatus(AcquisitionStatus.INVALID).build());
                }
                client.sendData(ProtoDataType.PARAMETER, pd.build());
            }
        } catch (NoPermissionException e) {
            throw new ProcessorException("No permission", e);
        }
    }

    @Override
    public void socketClosed() {
        if (pidrm != null) {
            pidrm.quit();
        }
    }
}
