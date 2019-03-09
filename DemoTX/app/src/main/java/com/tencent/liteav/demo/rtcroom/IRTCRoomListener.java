package com.tencent.liteav.demo.rtcroom;

import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;

import java.util.List;

/**
 * Created by jac on 2017/10/30.
 */

public interface IRTCRoomListener {

    /**
     * 获取房间成员通知
     * @param pusherList    房间成员列表
     */
    void onGetPusherList(List<PusherInfo> pusherList);

    /**
     * 新成员加入房间通知
     * @param pusherInfo    成员信息
     */

    void onPusherJoin(PusherInfo pusherInfo);
    /**
     * 成员离开房间通知
     * @param pusherInfo    成员信息
     */

    void onPusherQuit(PusherInfo pusherInfo);

    /**
     * 收到房间文本消息
     * @param roomID        房间ID
     * @param userID        发送者ID
     * @param userName      发送者昵称
     * @param userAvatar    发送者头像
     * @param message       文本消息
     */
    void onRecvRoomTextMsg(String roomID, String userID, String userName, String userAvatar, String message);

    /**
     * 收到房间自定义消息
     * @param roomID        房间ID
     * @param userID        发送者ID
     * @param userName      发送者昵称
     * @param userAvatar    发送者头像
     * @param cmd           自定义cmd
     * @param message       自定义消息内容
     */
    void onRecvRoomCustomMsg(String roomID, String userID, String userName, String userAvatar, String cmd, String message);

    /**
     * 收到房间解散通知
     * @param roomID        房间ID
     */
    void onRoomClosed(String roomID);

    /**
     * 日志回调
     * @param log           日志内容
     */
    void onDebugLog(String log);

    /**
     * 错误回调
     * @param errorCode     错误码
     * @param errorMessage  错误描述
     */
    void onError(int errorCode, String errorMessage);
}
