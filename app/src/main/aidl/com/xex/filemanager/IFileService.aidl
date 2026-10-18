package com.xex.filemanager;

import android.os.ParcelFileDescriptor;

interface IFileService {
    void destroy() = 16777114;
    String list(String path) = 1;
    String stat(String path) = 2;
    ParcelFileDescriptor openRead(String path) = 3;
    ParcelFileDescriptor openWrite(String path, boolean append) = 4;
    boolean mkdirs(String path) = 5;
    boolean delete(String path) = 6;
    boolean rename(String from, String to) = 7;
    boolean exists(String path) = 8;
    String exec(String command) = 9;
    int myUid() = 10;
}
