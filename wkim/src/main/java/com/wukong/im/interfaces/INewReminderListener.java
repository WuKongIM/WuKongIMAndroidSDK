package com.wukong.im.interfaces;

import com.wukong.im.entity.WKReminder;

import java.util.List;

public interface INewReminderListener {
    void newReminder(List<WKReminder> list);
}
