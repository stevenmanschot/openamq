import java.net.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import org.openamq.*;
import org.openamq.frames.*;

public class EchoClient extends amqpcli_serial implements Runnable {


///////////////////////////   P A R A M E T E R S   ///////////////////////////
// Some queue defaults for this client
String
    read_queue = "q-a",                 /* Queue to read from                */
    write_queue = "q-b";                /* Queue to write to                 */
boolean
    input = true,                       /* Verbose mode                      */
    sampleOutput = false;               /* Sample output switch (no AWT)     */
int
    test_dialog;    

    
//////////////////////////////   G L O B A L S   //////////////////////////////
String        
    client_name =                       /* Client name                       */
        "Java echo client";
// AWT UI
Frame 
    f;                                  /* The UI frame                     */
TextArea 
    ea;                                 /* Upper text echo area             */
TextField 
    tf;                                 /* Lower text input field           */
// Receive thread
Thread 
    receive_thread;                     /* Receives echo from server        */
// AMQ Channel
AMQChannel.Open
    channel_open;                       /* Channel open command             */
AMQChannel.Close
    channel_close;                      /* Channel close command            */
AMQChannel.Ack
    channel_ack;                        /* Channel ack command              */
// AMQ Handle
AMQHandle.Open
    handle_open;                        /* Handle open command              */
AMQHandle.Send
    handle_send;                        /* Handle send command              */
AMQHandle.Consume
    handle_consume;                     /* Handle consume command           */
AMQHandle.Notify
    handle_notify;                      /* Handle notify reply              */
AMQHandle.Created
    handle_created;                     /* Handle created reply             */
// AMQ Message
AMQMessage.Head
    message_head;                       /* Message header                   */
byte[]
    message_body;                       /* Message body                     */

    
/////////////////////////////////   MAIN   ////////////////////////////////////
 
public static void main(String[] args) {
    new EchoClient(args);
}

public int amqpcli_serial_execute (String args[])
{
    return execute ();
}


/////////////////////////////////   AWT CONSTRUCTOR  //////////////////////////

public EchoClient(String[] args) {
    // Command-line parameters
    final String
        USAGE =                             /* Usage                             */
            "Syntax: clientname [options...]\n"                                        +
            "Options:\n"                                                               +
            "  -c clientname    Client identifier (default: '" + client_name + "')\n"  +
            "  -s server        Name or address of server (localhost)\n"               +
            "  -t level         Set trace level (default = 0)\n"                       +
            "  -r name          Set read queue (default = '" + read_queue + "')\n"     +
            "  -w name          Set write queue (default = '" + write_queue + "')\n"   +
            "  -i               Disable the input field\n"                             +
            "  -o               Produce sample messages (no AWT)\n"                    +
            "  -q               Quiet mode: no messages\n"                             +
            "  -v               Show version information\n"                            +
            "  -h               Show summary of command-line options\n"                +
            "\nThe order of arguments is not important. Switches and filenames\n"      +
            "are case sensitive.\n",
        CLIENT_NAME_PRINT =             /* Full name for console             */
            client_name + " - " + AMQFramingFactory.VERSION + "\n";    
    String
        argparm = null;                 /* Command line argument             */
    boolean
        args_ok = true;                 /* Arguments parsing status          */

    for (int argn = 0; argn < args.length; argn++) {
        /*  If argparm is set, we have to collect an argument parameter      */
        if (argparm != null) {
            if (!args[argn].startsWith("-")) {  /*  Parameter can't start with '-'   */
                arguments.setProperty(argparm, args[argn]);
                argparm = null;
            } else {
                args_ok = false;
                break;
            }
        } else if (args[argn].startsWith("-")) {
            switch (args[argn].charAt(1)) {
                /*  These switches take a parameter                          */
                case 'c':
                    argparm = "opt_client";
                    break;
                case 's':
                    argparm = "opt_server";
                    break;
                case 't':
                    argparm = "opt_trace";
                    break;
                case 'r':
                    argparm = "read_queue";
                    break;
                case 'w':
                    argparm = "write_queue";
                    break;
                case 'd':
                    argparm = "test_dialog";
                    break;

                /*  These switches have an immediate effect                  */
                case 'i':
                    input = false;
                    break;
                case 'o':
                    sampleOutput = true;
                    break;
                case 'q':
                    quiet_mode = true;
                    break;
                case 'v':
                    System.out.println(CLIENT_NAME_PRINT);
                    System.out.println(COPYRIGHT);
                    System.out.println(NOWARRANTY);
                    System.exit(0);
                case 'h':
                    System.out.println(CLIENT_NAME_PRINT);
                    System.out.println(COPYRIGHT);
                    System.out.println(NOWARRANTY);
                    System.out.println(USAGE);
                    System.exit(0);

                /*  Anything else is an error                                */
                default:
                    args_ok = false;
            }
        } else {
            args_ok = false;
            break;
        }
    }
    /*  If there was a missing parameter or an argument error, quit          */
    if (argparm != null) {
        System.out.println("Argument missing - use -h for help");
        System.exit(1);
    } else if (!args_ok) {
        System.out.println("Invalid arguments - use -h for help");
        System.exit(1);
    }

    read_queue = arguments.getProperty("read_queue", read_queue);
    write_queue = arguments.getProperty("write_queue", write_queue);
    verbose = Integer.parseInt(arguments.getProperty("opt_trace", "0")) > 0;
    test_dialog = Integer.parseInt(arguments.getProperty("test_dialog", "-1"));
    
    // UI
    GridBagLayout 
        gbl;                            /* Frame's layout                   */
    GridBagConstraints 
        gbc;                            /* Layout configuration             */
    
    if (!sampleOutput) {        
        gbl = new GridBagLayout();
        gbc = new GridBagConstraints();
        ea = new TextArea("", 20, 80, TextArea.SCROLLBARS_VERTICAL_ONLY);
        ea.setEditable(false);
        tf = new TextField("type here (/quit to quit)", 80);
        // Configure the upper text area
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbl.setConstraints(ea, gbc);
        // Configure the lower text field
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        gbc.weighty = 0;
        if (input)
            gbl.setConstraints(tf, gbc);
        // Configure the frame
        f = new Frame(client_name + " - " + AMQFramingFactory.VERSION);
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (receive_thread != null) {
                    try {
                        // Say bye
                        channel_close.channelId = 1;
                        channel_close.replyCode = 200;
                        channel_close.replyText = "EchoClient.java: I'll be back";
                        amq_framing.sendFrame(channel_close);
                    } 
                    catch (IOException f)
                    {
                        raise_exception(exception_event, f, "EchoClient", "windowClosing", "error writing to server.\n");
                    }
                    catch (AMQException f)
                    {
                        raise_exception(exception_event, f, "EchoClient", "windowClosing", "framing error");
                    }
                } else {
                    System.exit(0);
                }
            }
        });
        f.setLayout(gbl);
        f.add(ea);
        if (input)
            f.add(tf);
        f.pack();
        // Show window
        f.setVisible(true);
        tf.requestFocus();
    } else if (test_dialog == 1) {
        PassFailFrame pf = new PassFailFrame("Queue distribution", "Did the received messages got distributed among the two consumer windows?", 
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!e.getActionCommand().equals("PASS")) 
                        error_message = "FAIL: user audit";
                    synchronized (receive_thread) {
                        receive_thread.notify();
                    }
                }
            }
        );
        pf.setVisible(true);
    }
    
    // Start AMQ layer
    amqpcli_serial_execute(args);
}


/////////////////////////////////   USER SENDS TEXT  //////////////////////////

class tfActionListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
        String                      /* Text to send to the server       */
            text = e.getActionCommand();
            
        // Echo local text in the echo area
        ea.append("< " + text + "\n");
        
        actionPerformed(text);
        
        // Clear the textfield   
        tf.setText("");
    }
    
    public void actionPerformed(String text) {
        try {
            if (text.equals("/quit")) {
                // Say bye
                channel_close.channelId = 1;
                channel_close.replyCode = 200;
                channel_close.replyText = "EchoClient.java: I'll be back";
                amq_framing.sendFrame(channel_close);
            } else {
                // Send text
                if (!quiet_mode)
                    System.out.println("Sending: \"" + text + "\" to server...");
                // Create the message body
                message_body = AMQFramingFactory.string2Bytes(text);
                message_head.bodySize = message_body.length;
                // Set the fragment size
                handle_send.fragmentSize = message_head.size() + message_head.bodySize;
                if (handle_send.fragmentSize <= amq_framing.getFrameMax()) {
                    // Send message
                    amq_framing.sendFrame(handle_send);
                    amq_framing.sendMessageHead(message_head);
                    amq_framing.sendData(message_body);
                    amq_framing.flush();
                } else {
                    System.err.println("EchoClient: text size not supported by EchoClient");
                }   
            }     
        }
        catch (ClassCastException f)
        {
            raise_exception(exception_event, f, "EchoClient", "tfActionListener", "unexpected frame from server");
        }
        catch (IOException f)
        {
            raise_exception(exception_event, f, "EchoClient", "tfActionListener", "error writing to server.\n");
        }
        catch (AMQException f)
        {
            raise_exception(exception_event, f, "EchoClient", "tfActionListener", "framing error");
        }
    }
}
 
    
/////////////////////////////////   GET TEXT  /////////////////////////////////

public void run () {
    try {
        String 
            text;                       /* Text received from the server    */
        byte[] 
            bytes;                      /* Bytes received from the server   */
        
        while(true) {
            AMQFrame frame;
            int close = 0; 
            // Get frame 
            frame = amq_framing.receiveFrame();
            if (frame instanceof AMQConnection.Close) {
                client_close = (AMQConnection.Close)frame;
                close = 1;
            } else if (frame instanceof AMQChannel.Close ) {
                channel_close = (AMQChannel.Close)frame;
                amq_framing.sendFrame(client_close);
                client_close = (AMQConnection.Close)amq_framing.receiveFrame();
                close = 2;
            }
            if (close > 0) {         
                if (close > 1)         
                    System.out.println("Channel closing, server says: " + channel_close.replyText + ".");
                System.out.println("Connection closing, server says: " + client_close.replyText + ".");
                if (test_dialog == -1) {
                    synchronized (receive_thread) {
                        receive_thread.notify();
                    }
                    break;
                } else {
                    try {
                        synchronized (receive_thread) {
                            receive_thread.wait();
                        }
                    } catch (InterruptedException e) {} 
                }
            }
            // Get the data
            handle_notify = (AMQHandle.Notify)frame;
            message_head = amq_framing.receiveMessageHead();
            bytes = new byte[(int)message_head.bodySize];
            amq_framing.receiveData(bytes);
            text = AMQFramingFactory.bytes2String(bytes);
            // Acknowledge 
            channel_ack.messageNbr = handle_notify.messageNbr;
            amq_framing.sendFrame(channel_ack);
            if (!quiet_mode)
                System.out.println("Acknowledge: \"" + text + "\" from server...");
            // Show text in the echo area
            ea.append("  > " + text + "\n");
        } 
    }
    catch (ClassCastException e)
    {
        raise_exception(exception_event, e, "EchoClient", "run", "unexpected frame from server");
    }
    catch (IOException e)
    {
        raise_exception(exception_event, e, "EchoClient", "run", "error reading from server");
    }
    catch (AMQException e)
    {
        raise_exception(exception_event, e, "EchoClient", "run", "framing error");
    }
}

     
/////////////////////////////////   DO TESTS   ////////////////////////////////

public void do_tests ()
{
    try
    {
        // Channel
        channel_open = (AMQChannel.Open)amq_framing.constructFrame(AMQChannel.OPEN);
        channel_close = (AMQChannel.Close)amq_framing.constructFrame(AMQChannel.CLOSE);
        channel_ack = (AMQChannel.Ack)amq_framing.constructFrame(AMQChannel.ACK);
        // Handle
        handle_open = (AMQHandle.Open)amq_framing.constructFrame(AMQHandle.OPEN);
        handle_send = (AMQHandle.Send)amq_framing.constructFrame(AMQHandle.SEND);
        handle_consume = (AMQHandle.Consume)amq_framing.constructFrame(AMQHandle.CONSUME);
        // Message
        message_head = (AMQMessage.Head)amq_framing.constructMessageHead();
        
        // Open channel
        channel_open.channelId = 1;
        channel_open.confirmTag = 0;
        channel_open.transacted = false;
        channel_open.restartable = false;
        channel_open.options = null;
        channel_open.outOfBand = "";
        amq_framing.sendFrame(channel_open);

        // Open hadles ... 
        handle_open.channelId = 1;
        handle_open.serviceType = 1;
        handle_open.confirmTag = 0;
        handle_open.browser = false;
        handle_open.mimeType = "";
        handle_open.encoding = "";
        handle_open.options = null;
        // ... for reading
        handle_open.handleId = 1;
        handle_open.producer = false;
        handle_open.consumer = true;
        handle_open.temporary = false;
        amq_framing.sendFrame(handle_open);
        if (handle_open.temporary) {
            // Get handle created
            handle_created = (AMQHandle.Created)amq_framing.receiveFrame();
        }
        // ... for writing
        handle_open.handleId = 2;
        handle_open.producer = true;
        handle_open.consumer = false;
        handle_open.temporary = false;
        amq_framing.sendFrame(handle_open);
        if (handle_open.temporary) {
            // Get handle created
            handle_created = (AMQHandle.Created)amq_framing.receiveFrame();
        }

        // Prepare ack
        channel_ack.channelId = 1;
        channel_ack.confirmTag = 0;
        channel_ack.messageNbr = 0;
    
        // Prepare for sending
        handle_send.handleId = 2;
        handle_send.confirmTag = 0;
        handle_send.fragmentSize = 0;
        handle_send.partial = false;
        handle_send.outOfBand = false;
        handle_send.recovery = false;
        handle_send.immediate = true;
        handle_send.destName = write_queue;
        message_head.bodySize = 0;
        message_head.persistent = false;
        message_head.priority = 1;
        message_head.expiration = 0;
        message_head.mimeType = "";
        message_head.encoding = "";
        message_head.identifier = "";
        message_head.headers = null;
    
        // Prepare for consuming
        handle_consume.handleId = 1;
        handle_consume.confirmTag = 0;
        handle_consume.prefetch = 1;
        handle_consume.noLocal = true;
        handle_consume.noAck = false;
        handle_consume.dynamic = true;
        handle_consume.destName = read_queue;
        handle_consume.selector = null;
        
        // Request consume messages
        amq_framing.sendFrame(handle_consume);
        
        // Read text thread
        receive_thread = new Thread(this);
        receive_thread.start();
        
        tfActionListener tfa = new tfActionListener();
        if (!sampleOutput) {
            // Send text in AWT thread
            tf.addActionListener(tfa);
        } else {
            // Send some sample text
            tfa.actionPerformed(" 1 Science! true daughter of Old Time thou art!");
            tfa.actionPerformed(" 2    Who alterest all things with thy peering eyes.");
            tfa.actionPerformed(" 3 Why preyest thou thus upon the poet's heart,");
            tfa.actionPerformed(" 4    Vulture, whose wings are dull realities?");
            tfa.actionPerformed(" 5 How should he love thee? or how deem thee wise,");
            tfa.actionPerformed(" 6    Who wouldst not leave him in his wandering");
            tfa.actionPerformed(" 7 To seek for treasure in the jewelled skies,");
            tfa.actionPerformed(" 8    Albeit he soared with an undaunted wing?");
            tfa.actionPerformed(" 9 Hast thou not dragged Diana from her car?");
            tfa.actionPerformed("10    And driven the Hamadryad from the wood");
            tfa.actionPerformed("11 To seek a shelter in some happier star?");
            tfa.actionPerformed("12    Hast thou not torn the Naiad from her flood,");
            tfa.actionPerformed("13 The Elfin from the green grass, and from me");
            tfa.actionPerformed("14 The summer dream beneath the tamarind tree?");         
            tfa.actionPerformed("15 Edgar Allan Poe");
            tfa.actionPerformed("/quit");     
        }
        synchronized (receive_thread) {
            try {
                receive_thread.wait();
            } catch (InterruptedException e)  {}
        }
    }
    catch (ClassCastException e)
    {
        raise_exception(exception_event, e, "amqpci_java", "do_tests", "unexpected frame from server");
    }
    catch (SocketTimeoutException e) {
        raise_exception(timeout_event, e, "amqpci_java", "do_tests", "SocketTimeoutException");
    }
    catch (IOException e)
    {
        raise_exception(exception_event, e, "amqpci_java", "do_tests", "IOException");
    }
    catch (AMQException e)
    {
        raise_exception(exception_event, e, "amqpci_java", "do_tests", "framing error");
    }

    the_next_event = done_event;
}

class PassFailFrame extends Frame {

  Button 
    pass = new Button("PASS");
  Button 
    fail = new Button("FAIL");

  public PassFailFrame(String title, String message, ActionListener al) {
    super(title);
    setLayout(new BorderLayout());
    add(BorderLayout.CENTER, new Label(message));
    Panel p = new Panel();
    p.setLayout(new FlowLayout());
    fail.addActionListener(al);
    p.add(fail);
    pass.addActionListener(al);
    p.add(pass);
    add(BorderLayout.SOUTH, p);
    pack();
  }  
}        


// End EchoClient
}
