package io.nuls.rpc.server;

import io.nuls.rpc.info.Constants;
import io.nuls.rpc.model.message.Message;
import io.nuls.rpc.model.message.Request;
import io.nuls.tools.log.Log;
import io.nuls.tools.parse.JSONUtils;
import org.java_websocket.WebSocket;

import java.util.Map;

/**
 * 处理客户端消息的线程
 * Threads handling client messages
 *
 * @author tangyi
 * @date 2018/11/7
 * @description
 */
public class RequestLoopProcessor implements Runnable {

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {

        while (true) {
            try {
                /*
                获取队列中的第一个对象，如果是空，舍弃
                Get the first item of the queue, If it is an empty object, discard
                 */
                Object[] objects = ServerRuntime.firstObjArrInRequestLoopQueue();
                if (objects == null) {
                    Thread.sleep(Constants.INTERVAL_TIMEMILLIS);
                    continue;
                }

                WebSocket webSocket = (WebSocket) objects[0];
                String msg = (String) objects[1];

                Message message = JSONUtils.json2pojo(msg, Message.class);
                Request request = JSONUtils.map2pojo((Map) message.getMessageData(), Request.class);
                if (Constants.booleanString(true).equals(request.getRequestAck())) {
                    /*
                    如果需要一个Ack，则发送
                    Send Ack if needed
                     */
                    CmdHandler.ack(webSocket, message.getMessageId());

                    /*
                    Ack只发送一次（发送之后改变requestAck的值为0）
                    Ack is sent only once (change the value of requestAck to 0 after sending)
                     */
                    request.setRequestAck(Constants.booleanString(false));
                }
                message.setMessageData(request);

                /*
                Request，调用本地方法
                If it is Request, call the local method
                 */
                if (CmdHandler.response(webSocket, message.getMessageId(), request)) {
                    /*
                    需要继续发送，添加回队列
                    Need to continue sending, add back to queue
                     */
                    ServerRuntime.REQUEST_LOOP_QUEUE.offer(new Object[]{webSocket, JSONUtils.obj2json(message)});
                }

                Thread.sleep(Constants.INTERVAL_TIMEMILLIS);
            } catch (Exception e) {
                Log.error(e);
            }
        }
    }
}