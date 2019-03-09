package com.tencent.liteav.demo.rtcroom.ui.double_room;

import com.tencent.liteav.demo.rtcroom.RTCRoom;

/**
 * Created by jac on 2017/11/1.
 * Copyright © 2013-2017 Tencent Cloud. All Rights Reserved.
 */

public interface RTCDoubleRoomActivityInterface {
    RTCRoom getRTCRoom();
    String  getSelfUserID();
    String  getSelfUserName();
    void    showGlobalLog(boolean enable);
    void    printGlobalLog(String format, Object... args);
    void    setTitle(String s);
}
