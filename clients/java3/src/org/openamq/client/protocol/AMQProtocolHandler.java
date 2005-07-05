package org.openamq.client.protocol;

import edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArraySet;
import org.apache.log4j.Logger;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.protocol.ProtocolHandler;
import org.apache.mina.protocol.ProtocolSession;
import org.openamq.client.AMQConnection;
import org.openamq.client.AMQSession;
import org.openamq.client.AMQException;
import org.openamq.client.framing.AMQCommandFrame;
import org.openamq.client.framing.AMQFrame;
import org.openamq.client.framing.Connection;
import org.openamq.client.state.AMQState;
import org.openamq.client.state.AMQStateManager;
import org.openamq.client.state.listener.ChannelCloseListener;
import org.openamq.client.state.listener.ConnectionCloseListener;

import java.util.Iterator;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class AMQProtocolHandler implements ProtocolHandler
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolHandler.class);

    /**
     * The connection that this protocol handler is associated with. There is a 1-1
     * mapping between connection instances and protocol handler instances.
     */
    private AMQConnection _connection;

    /**
     * Our wrapper for a protocol session that provides access to session values
     * in a typesafe manner.
     */
    private AMQProtocolSession _protocolSession;

    private final AMQStateManager _stateManager = new AMQStateManager();

    private final CopyOnWriteArraySet _frameListeners = new CopyOnWriteArraySet();

    public AMQProtocolHandler(AMQConnection con)
    {
        _connection = con;
        _frameListeners.add(_stateManager);
    }

    public void sessionCreated(ProtocolSession session) throws Exception
    {
        _protocolSession = new AMQProtocolSession(session, _connection);
    }

    public void sessionOpened(ProtocolSession session) throws Exception
    {
        // TODO: remove once upgraded to MINA 0.7.2
	// Then sessionCreated should get called.
	// This null check suffices for now across both versions.
	if (_protocolSession == null)
	{
		_protocolSession = new AMQProtocolSession(session, _connection);
	}
    }

    public void sessionClosed(ProtocolSession session) throws Exception
    {
        _logger.info("Protocol Session closed");
    }

    public void sessionIdle(ProtocolSession session, IdleStatus status) throws Exception
    {
        _logger.info("Protocol Session idle");
    }

    public void exceptionCaught(ProtocolSession session, Throwable cause) throws Exception
    {
        _logger.error("Exception caught by protocol handler: " + cause, cause);
        _connection.exceptionReceived(cause);
    }

    public void messageReceived(ProtocolSession session, Object message) throws Exception
    {
        Iterator it = _frameListeners.iterator();
        final FrameEvent evt = new FrameEvent((AMQFrame) message, _protocolSession);
        try
        {
            while (it.hasNext())
            {
                final FrameListener listener = (FrameListener) it.next();
                listener.frameReceived(evt);
            }
        }
        catch (AMQException e)
        {
            it = _frameListeners.iterator();
            while (it.hasNext())
            {
                final FrameListener listener = (FrameListener) it.next();
                listener.error(e);
            }
        }
    }

    public void messageSent(ProtocolSession session, Object message) throws Exception
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sent frame " + message);
        }
    }

    public void addFrameListener(FrameListener listener)
    {
        _frameListeners.add(listener);
    }

    public void removeFrameListener(FrameListener listener)
    {
        _frameListeners.remove(listener);
    }

    public void attainState(AMQState s) throws AMQException
    {
        _stateManager.attainState(s);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent
     * to calling getProtocolSession().write().
     *
     * @param frame the frame to write
     */
    public void writeFrame(AMQFrame frame)
    {
        _protocolSession.writeFrame(frame);
    }

    /**
     * Convenience method that writes a frame to the protocol session and waits for
     * a particular response. Equivalent to calling getProtocolSession().write() then
     * waiting for the response.
     * @param frame
     * @param listener the blocking listener. Note the calling thread will block.
     */
    public void writeCommandFrameAndWaitForReply(AMQCommandFrame frame,
                                                 BlockingCommandFrameListener listener)
        throws AMQException
    {
        _frameListeners.add(listener);
        _protocolSession.writeFrame(frame);
        listener.blockForFrame();
        // When control resumes at this point, a reply will have been received
        // that matches the criteria defined in the blocking listener
    }

    /**
     * Convenience method to register an AMQSession with the protocol handler. Registering
     * a session with the protocol handler will ensure that messages are delivered to the
     * consumer(s) on that session.
     *
     * @param handleId the handle id of the consumer
     * @param session the session instance.
     */
    public void addSessionByHandle(int handleId, AMQSession session)
    {
        _protocolSession.addSessionByHandle(handleId, session);
    }

    /**
     * Convenience method to deregister an AMQSession with the protocol handler.
     * @param handleId
     */
    public void removeSessionByHandle(int handleId)
    {
        _protocolSession.removeSessionByHandle(handleId);
    }


    /**
     * Convenience method to register an AMQSession with the protocol handler. Registering
     * a session with the protocol handler will ensure that messages are delivered to the
     * consumer(s) on that session.
     *
     * @param channelId the channel id of the session
     * @param session the session instance.
     */
    public void addSessionByChannel(int channelId, AMQSession session)
    {
        _protocolSession.addSessionByChannel(channelId, session);
    }

    /**
     * Convenience method to deregister an AMQSession with the protocol handler.
     * @param channelId then channel id of the session
     */
    public void removeSessionByChannel(int channelId)
    {
        _protocolSession.removeSessionByChannel(channelId);
    }

    public void closeSession(AMQSession session) throws AMQException
    {
        BlockingCommandFrameListener listener = new ChannelCloseListener(session.getChannelId());
        _frameListeners.add(listener);
        _protocolSession.closeSession(session);
        _logger.debug("Blocking for channel close frame for channel " + session.getChannelId());
        listener.blockForFrame();
        _logger.debug("Received channel close frame");
        // When control resumes at this point, a reply will have been received that
        // indicates the broker has closed the channel successfully
    }

    public void closeConnection() throws AMQException
    {
        BlockingCommandFrameListener listener = new ConnectionCloseListener();
        _frameListeners.add(listener);
        _stateManager.changeState(AMQState.CONNECTION_CLOSING);
        final Connection.Close frame = new Connection.Close();
        writeFrame(frame);
        _logger.debug("Blocking for connection close frame");
        listener.blockForFrame();
        _protocolSession.closeProtocolSession();
    }
}