<?xml?>
<class
    name      = "amq_sclient"
    comment   = "AMQP synchronous client API"
    version   = "1.1"
    copyright = "Copyright (c) 2004-2005 JPMorgan"
    script    = "icl_gen"
    >

<inherit class = "icl_alloc_cache" />
<option name = "nullify" value = "1" />

<import class = "ipr_classes" />
<import class = "smt_thread"  />
<import class = "amq_message" />

<private>
#include "amq_sclient_agent.h"

#define CHANNEL_ID          1
#define CHANNEL_TRANSACTED  TRUE
</private>

<public>
typedef enum {
    AMQ_SCLIENT_HANDLE_NOTIFY
} amq_sclient_callback_t;

typedef struct {
    amq_sclient_t *client;
    dbyte          handle_id;
    qbyte          message_nbr;
    Bool           delivered;
    Bool           redelivered;
    ipr_shortstr_t dest_name;
    amq_message_t *message;
} amq_sclient_handle_notify_t;

typedef void (amq_sclient_handle_notify_fn) (amq_sclient_handle_notify_t *args);
</public>

<context>
    smt_thread_handle_t
        *thread_handle;
    dbyte
        cur_handle;
    int
        reply_code;                     /*  Last reply code                  */
    ipr_shortstr_t
        reply_text;                     /*  Last reply text                  */
    amq_message_t
        *msg_object;                    /*  Last received message            */
    dbyte
        msg_handle;                     /*  Handle it was sent on            */
    qbyte
        msg_number;                     /*  Its message_nbr property         */
    ipr_shortstr_t
        msg_sender;                     /*  Its dest_name property           */
    Bool
        msg_delivered;                  /*  Its delivered property           */
    Bool
        msg_redelivered;                /*  Its redelivered property         */
</context>

<method name = "trace">
    <argument name = "trace" type = "int">Trace level, 0 to 3</argument>
    if (!s_class_active)
        s_class_initialise (trace);
</method>

<method name = "new">
    <argument name = "client name" type = "char *">Client identifier</argument>
    <argument name = "login"       type = "char *">User login name</argument>
    <argument name = "password"    type = "char *">User password</argument>
    ASSERT (client_name && *client_name);

    if (!s_class_active)
        s_class_initialise (0);

    self->cur_handle = 0;
    self->thread_handle = smt_thread_handle_new (
        amq_sclient_agent_client_thread_new (self, client_name, login, password));
    amq_sclient_agent_register (
        self->thread_handle, AMQ_SCLIENT_HANDLE_NOTIFY, s_handle_notify);

    smt_thread_execute (SMT_EXEC_FULL);
    if (!smt_thread_handle_valid (self->thread_handle))
        $(selfname)_destroy (&self);
</method>

<method name = "destroy">
    if (smt_thread_handle_valid (self->thread_handle))
        smt_thread_destroy (&self->thread_handle->thread);
    smt_thread_handle_destroy (&self->thread_handle);
    amq_message_destroy       (&self->msg_object);
</method>

<method name = "connect" template = "function">
    <doc>
    Connects to the remote AMQP server and virtual host specified.  Returns
    TRUE if the connection succeeded.
    </doc>
    <argument name = "hostname"     type = "char *">Server to connect to</argument>
    <argument name = "virtual path" type = "char *">Virtual host path</argument>

    if (hostname == NULL || *hostname == 0)
        hostname = "localhost";
    if (virtual_path == NULL || *virtual_path == 0)
        virtual_path = "/";

    amq_sclient_agent_connection_open (
        self->thread_handle, hostname, virtual_path, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (rc == AMQ_OK) {
        amq_sclient_agent_channel_open (
            self->thread_handle, CHANNEL_ID, CHANNEL_TRANSACTED, FALSE, &rc);
        smt_thread_execute (SMT_EXEC_FULL);
    }
    else
    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "producer" template = "function">
    <argument name = "destination" type = "char *">Destination to work with</argument>
    ASSERT (destination && *destination);

    amq_sclient_agent_handle_open (
        self->thread_handle, CHANNEL_ID, ++self->cur_handle, FALSE, destination, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (rc == AMQ_OK)
        rc = self->cur_handle;
    else
        rc = 0;
</method>

<method name = "consumer" template = "function">
    <argument name = "destination" type = "char *">Destination to work with</argument>
    <argument name = "prefetch"    type = "int"   >Prefetch window size</argument>
    <argument name = "noack"       type = "Bool"  >No acknowledgements required</argument>
    ASSERT (destination && *destination);

    amq_sclient_agent_handle_open (
        self->thread_handle, CHANNEL_ID, ++self->cur_handle, FALSE, destination, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (rc == AMQ_OK) {
        amq_sclient_agent_handle_consume (
            self->thread_handle, self->cur_handle, (dbyte) prefetch, TRUE, noack, NULL, NULL, &rc);
        smt_thread_execute (SMT_EXEC_FULL);
    }
    else
    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);

    if (rc == AMQ_OK)
        rc = self->cur_handle;
    else
        rc = 0;
</method>

<method name = "temporary" template = "function">
    <argument name = "destination" type = "char *">Destination to create</argument>
    <argument name = "prefetch"    type = "int"   >Prefetch window size</argument>
    <argument name = "noack"       type = "Bool"  >No acknowledgements required</argument>
    ASSERT (destination && *destination);

    amq_sclient_agent_handle_open (
        self->thread_handle, CHANNEL_ID, ++self->cur_handle, TRUE, destination, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (rc == AMQ_OK) {
        amq_sclient_agent_handle_consume (
            self->thread_handle, self->cur_handle, (dbyte) prefetch, TRUE, noack, NULL, NULL, &rc);
        smt_thread_execute (SMT_EXEC_FULL);
    }
    else
    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);

    if (rc == AMQ_OK)
        rc = self->cur_handle;
    else
        rc = 0;
</method>

<method name = "msg send" template = "function">
    <doc>
    Sends a message to the destination specified by the handle. The message
    object is taken over by the method and eventually destroyed - the caller
    should create a new message object for each msg_send call.
    </doc>
    <argument name = "handle id" type = "dbyte"          >Handle id</argument>
    <argument name = "message"   type = "amq_message_t *">Message to send</argument>
    ASSERT (message);

    amq_sclient_agent_handle_send (
        self->thread_handle, handle_id, message, NULL, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "msg read" return = "message">
    <doc>
    Waits until a message arrives from the server, and delivers it to the
    caller.  Each incoming message is freshly allocated; the caller should
    destroy it when finished processing it.
    </doc>
    <argument name = "self"    type = "$(selftype) *" />
    <argument name = "timeout" type = "qbyte"          >Timeout in milliseconds</argument>
    <declare  name = "message" type = "amq_message_t *">Message to provide</declare>
    <local>
        int rc;
    </local>
    amq_message_destroy (&self->msg_object);
    amq_sclient_agent_blocking_receive (self->thread_handle, timeout, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    /*  We return the message explicitly and other properties via context    */
    message = self->msg_object;

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "msg ack" template = "function">
    amq_sclient_agent_channel_ack (
        self->thread_handle, CHANNEL_ID, self->msg_number, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "msg unget" template = "function">
    amq_sclient_agent_handle_unget (
        self->thread_handle, self->cur_handle, self->msg_number, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "commit" template = "function">
    amq_sclient_agent_channel_commit (
        self->thread_handle, CHANNEL_ID, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "rollback" template = "function">
    amq_sclient_agent_channel_rollback (
        self->thread_handle, CHANNEL_ID, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "flow" template = "function">
    <argument name = "handle id"  type = "dbyte">Handle id, 0 means all</argument>
    <argument name = "flow pause" type = "Bool" >Pause messages?</argument>
    amq_sclient_agent_handle_flow (
        self->thread_handle, handle_id, flow_pause, &rc);
    smt_thread_execute (SMT_EXEC_FULL);

    if (self->reply_code)
        coprintf ("E: %d - %s", self->reply_code, self->reply_text);
</method>

<method name = "close" template = "function">
    <argument name = "handle id" type = "dbyte" >Handle id, 0 means all</argument>
    if (handle_id)
        amq_sclient_agent_handle_close (self->thread_handle, handle_id, &rc);
    else
        amq_sclient_agent_connection_close (self->thread_handle, &rc);

    smt_thread_execute (SMT_EXEC_FULL);
</method>

<private name = "header">
/*  Class initialisation management                                          */

static Bool
    s_class_active = FALSE;
static inline void
    s_class_initialise (int trace);
static void
    s_class_terminate (void);
static void
    s_handle_notify (amq_sclient_handle_notify_t *args);
</private>

<private name = "footer">
static void
s_class_initialise (int trace)
{
    s_class_active = TRUE;

    /*  Start the client agent - gets stopped by icl_system               */
    amq_sclient_agent_init (trace);

    /*  Register the class termination call-back functions                   */
    icl_system_register (NULL, s_class_terminate);
}

static void
s_class_terminate (void)
{
    s_class_active = FALSE;
}

/*  This callback populates the object context with the notification
    properties including the message itself.  The caller gets possession
    of the message and has to destroy it when finished with it.              */

static void
s_handle_notify (amq_sclient_handle_notify_t *args)
{
    $(selftype)
        *self;

    self = args->client;
    self->msg_object      = args->message;
    self->msg_handle      = args->handle_id;
    self->msg_number      = args->message_nbr;
    ipr_shortstr_cpy (self->msg_sender, args->dest_name);
    self->msg_delivered   = args->delivered;
    self->msg_redelivered = args->redelivered;
}
</private>

<method name = "selftest">
    <!-- see amqpcli.c -->
</method>

</class>
