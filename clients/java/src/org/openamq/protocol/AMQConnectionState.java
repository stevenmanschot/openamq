package org.openamq.protocol;

import java.awt.*;
import java.applet.*;
import java.net.*;
import java.util.*;
import java.io.*;

import org.apache.log4j.Logger;

import org.openamq.framing.*;
import org.openamq.*;

public class AMQConnectionState extends AMQConnectionStateI implements Runnable
{

//////////////////////////////   G L O B A L S   //////////////////////////////

private static final Logger
    _logger = Logger.getLogger(AMQConnectionState.class);

AMQClientConnection
    acc;
AMQProtocolHandler
    aph;
HashMap
    methodToEvent;
LinkedList
    frames;
AMQFrame
    frame;
AMQMethodBody
    amb = null;
boolean
    expectExternalEvent,
    connectionOpened,
    connectionOpening;

///////////////////////////   C O N T R U C T O R   ///////////////////////////

public AMQConnectionState (AMQClientConnection acc)
{
    this.acc = acc;
    this.aph = acc.getProtocolHandler();
    methodToEvent = new HashMap();
    frames = new LinkedList();
    frame = null;
    expectExternalEvent = false;
    connectionOpened = false;
    connectionOpening = true;

    methodToEvent.put(ConnectionStartBody.class, new Integer(ProtocolInitiationOkEvent));
    methodToEvent.put(ConnectionTuneBody.class, new Integer(ConnectionTuneEvent));
    methodToEvent.put(ConnectionOpenOkBody.class, new Integer(ConnectionOpenOkEvent));
    methodToEvent.put(ConnectionCloseBody.class, new Integer(ConnectionCloseEvent));
    methodToEvent.put(ConnectionCloseOkBody.class, new Integer(ConnectionFinishedEvent));
}

//////////////////////////////////   M A I N   ////////////////////////////////

public void run ()
{
    execute();
}

//////////////////////////   INITIALISE THE PROGRAM   /////////////////////////

public void initialiseTheProgram ()
{
    theNextEvent = okEvent;
}

////////////////////////////   SET EXTERNAL EVENT   ///////////////////////////

public void setExternalEvent (AMQFrame frame)
{
    synchronized (frames) {
        frames.add(frame);
        frames.notifyAll();
    }
}

////////////////////////////   GET EXTERNAL EVENT   ///////////////////////////

public void getExternalEvent ()
{
    synchronized (frames) {
        while (expectExternalEvent) {
            if (frames.isEmpty()) {
                try {
                    frames.wait();
                } catch (InterruptedException e) {}
            } else {
                frame = (AMQFrame)frames.removeFirst();
                expectExternalEvent = false;
            }
        }
    }

    try {
        if (frame != null) {
            if (frame.bodyFrame instanceof HeartbeatBody) {
                theNextEvent = connectionOpenOkEvent;
            } else if (frame.bodyFrame instanceof AMQMethodBody) {
                amb = (AMQMethodBody)frame.bodyFrame;

                if (amb.getClassId() == ConnectionStartBody.CLASS_ID) {
                    // Get connection event
                    Integer event = (Integer)methodToEvent.get(amb.getClass());

                    if (event != null)
                    {
                        theNextEvent = event.intValue();
                    }
                    else
                    {
                        _logger.debug("No special action for frame (at connection level): " + frame);
                        theNextEvent = connectionOpenOkEvent;
                    }
                } else {
                    // Dispatch to sessions
                    acc.dispatchFrame(frame);
                    theNextEvent = connectionOpenOkEvent;
                }
            } else if (frame.bodyFrame instanceof ContentBody || frame.bodyFrame instanceof BasicContentHeaderBody) {
                // Dispatch to sessions
                acc.dispatchFrame(frame);
                theNextEvent = connectionOpenOkEvent;
            } else {
                acc.close(AMQConstant.NOT_ALLOWED, "Frame not allowed (at connection level): " + frame, 0, 0);
                Cleanup();
                RaiseException(0);
            }
        } else {
            int
                errorCode;
            String
                errorMessage;

            synchronized (frames) {
                if (frames.isEmpty()) {
                    errorCode = AMQConstant.INTERNAL_ERROR;
                    errorMessage = "FSM error (at connection level)";
                } else {
                    errorCode = AMQConstant.NOT_ALLOWED;
                    errorMessage = "Frame not allowed (at connection level): " + frame;
                }
            }
            acc.close(errorCode, errorMessage, 0, 0);
            Cleanup();
            RaiseException(0);
        }
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

///////////////////////   WAIT FOR CONNECTION OPENED   ////////////////////////

public void waitconnectionOpened ()
{
    synchronized (this) {
        while (!connectionOpened) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }
}

///////////////////////   CHECK FOR CONNECTION OPENED   ///////////////////////

public boolean isconnectionOpened ()
{
    synchronized (this) {
        return connectionOpened;
    }
}

///////////////////////   CHECK FOR CONNECTION OPENING   //////////////////////

public boolean isconnectionOpening ()
{
    synchronized (this) {
        return connectionOpening;
    }
}

//%START MODULE

/////////////////////////   SEND PROTOCOL INITIATION   ////////////////////////

public void sendProtocolInitiation ()
{
    // Send protocol initialization
    try {
        aph.writeFrame(null, new ProtocolInitiation());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

/////////////////////////////   CONNECTION START   ////////////////////////////

public void connectionStartOk ()
{
    ConnectionStartBody
        csb = (ConnectionStartBody)amb;
    FieldTable
        client = new FieldTable();
    AuthData
        ad = acc.getAuthData();

    client.put("instance", acc.getClientInstance());
    client.put("product", "OpenAMQ/J");
    client.put("version", "0.1a");
    client.put("platform", "java");
    client.put("copyright", "(C) iMatix 2006");
    try {
        aph.writeFrame(null, ConnectionStartOkBody.createAMQFrame(0, client, ad.getAuthMechanism(), ad.getAuthResponse(), "en_US"));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

/////////////////////////////   CONNECTION OPEN   /////////////////////////////

public void connectionOpen ()
{
    try {
        aph.writeFrame(null, ConnectionOpenBody.createAMQFrame(0, acc.getVirtualHost(), null, true));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

////////////////////////   CONNECTION OPEN OK HANDLER   ///////////////////////

public void connectionOpenOkHandler ()
{
    synchronized (this) {
        connectionOpened = true;
        connectionOpening = false;
        notifyAll();
    }
}

///////////////////////////   CONNECTION CLOSE OK   ///////////////////////////

public void connectionCloseOk ()
{
    try {
        aph.writeFrame(null, ConnectionCloseOkBody.createAMQFrame(0));
        theNextEvent = connectionFinishedEvent;
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

/////////////////////////////////   CLEAN UP   ////////////////////////////////

public void cleanup ()
{
    synchronized (this) {
        connectionOpened = false;
        connectionOpening = false;
    }
    aph.closeProtocolSession();
    theNextEvent = terminateEvent;
}

////////////////////////////   CONNECTION TUNE OK   ///////////////////////////

public void connectionTuneOk ()
{
    ConnectionTuneBody
        ctb = (ConnectionTuneBody)amb;

    acc.setConnectionTuneData(ctb);
    try {
        aph.writeFrame(null, ConnectionTuneOkBody.createAMQFrame(0, ctb.channelMax, ctb.frameMax, ctb.heartbeat));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

///////////////////////////////   EXPECT FRAME   //////////////////////////////

public void expectFrame ()
{
    if (IsconnectionOpened() || IsconnectionOpening())
    {
        synchronized (frames) {
            expectExternalEvent = true;
        }
    }
}

//%END MODULE
}
