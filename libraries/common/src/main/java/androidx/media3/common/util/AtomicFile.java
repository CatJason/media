package androidx.media3.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 一个辅助类，用于通过创建备份文件来执行原子文件操作，直到写入成功完成。
 *
 * <p>原子文件通过确保文件已完全写入并同步到磁盘后，再删除其备份来保证文件的完整性。只要备份文件存在，原始文件就被认为是无效的（是之前尝试写入文件时留下的）。
 *
 * <p>原子文件不提供任何文件锁定语义。当文件可能被多个线程或进程并发访问或修改时，请勿使用此类。调用者负责在访问文件时确保适当的互斥不变量。
 */
@UnstableApi
public final class AtomicFile {

  private static final String TAG = "AtomicFile";

  private final File baseName;
  private final File backupName;

  /**
   * 为指定路径的文件创建一个新的 AtomicFile。备份文件将在相同路径下附加 ".bak"。
   */
  public AtomicFile(File baseName) {
    this.baseName = baseName;
    backupName = new File(baseName.getPath() + ".bak");
  }

  /** 返回文件或其备份是否存在。 */
  public boolean exists() {
    return baseName.exists() || backupName.exists();
  }

  /** 删除原子文件。这会同时删除基础文件和备份文件。 */
  public void delete() {
    baseName.delete();
    backupName.delete();
  }

  /**
   * 开始对文件进行新的写入操作。此方法返回一个 {@link OutputStream}，您可以将新文件数据写入其中。如果数据成功写入，您<em>必须</em>调用 {@link #endWrite(OutputStream)}。在失败时，您应仅调用 {@link OutputStream#close()} 以释放其占用的资源。
   *
   * <p>示例用法：
   *
   * <pre>
   *   DataOutputStream dataOutput = null;
   *   try {
   *     OutputStream outputStream = atomicFile.startWrite();
   *     dataOutput = new DataOutputStream(outputStream); // 包装流
   *     dataOutput.write(data1);
   *     dataOutput.write(data2);
   *     atomicFile.endWrite(dataOutput); // 传递包装流
   *   } finally{
   *     if (dataOutput != null) {
   *       dataOutput.close();
   *     }
   *   }
   * </pre>
   *
   * <p>请注意，如果另一个线程当前正在执行写入操作，这将简单地用此线程正在写入的新文件替换该线程正在写入的内容，当另一个线程完成写入时，新的写入操作将不再安全（或将被丢失）。您必须自行对 AtomicFile 的访问进行线程保护。
   */
  public OutputStream startWrite() throws IOException {
    // 重命名当前文件，以便在下次读取时可用作备份
    if (baseName.exists()) {
      if (!backupName.exists()) {
        if (!baseName.renameTo(backupName)) {
          Log.w(TAG, "无法将文件 " + baseName + " 重命名为备份文件 " + backupName);
        }
      } else {
        baseName.delete();
      }
    }
    OutputStream str;
    try {
      str = new AtomicFileOutputStream(baseName);
    } catch (FileNotFoundException e) {
      File parent = baseName.getParentFile();
      if (parent == null || !parent.mkdirs()) {
        throw new IOException("无法创建 " + baseName, e);
      }
      // 现在已创建父目录，重试。
      try {
        str = new AtomicFileOutputStream(baseName);
      } catch (FileNotFoundException e2) {
        throw new IOException("无法创建 " + baseName, e2);
      }
    }
    return str;
  }

  /**
   * 当您成功完成对 {@link #startWrite()} 返回的流的写入时调用此方法。这将关闭、同步并提交新数据。下次尝试读取原子文件时将返回新的文件流。
   *
   * @param str 用于写入 {@link #startWrite()} 返回的流的最外层包装 OutputStream。
   * @see #startWrite()
   */
  public void endWrite(OutputStream str) throws IOException {
    str.close();
    // 如果 close() 抛出异常，则跳过下一行。
    backupName.delete();
  }

  /**
   * 打开原子文件进行读取。如果之前存在不完整的写入，这将回滚到最后一个有效数据，然后打开以供读取。
   *
   * <p>请注意，如果另一个线程当前正在执行写入操作，这将错误地认为它处于写入失败的状态并回滚，导致当前正在写入的新数据被丢弃。您必须自行对 AtomicFile 的访问进行线程保护。
   */
  public InputStream openRead() throws FileNotFoundException {
    restoreBackup();
    return new FileInputStream(baseName);
  }

  private void restoreBackup() {
    if (backupName.exists()) {
      baseName.delete();
      backupName.renameTo(baseName);
    }
  }

  private static final class AtomicFileOutputStream extends OutputStream {

    private final FileOutputStream fileOutputStream;
    private boolean closed = false;

    public AtomicFileOutputStream(File file) throws FileNotFoundException {
      fileOutputStream = new FileOutputStream(file);
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      flush();
      try {
        fileOutputStream.getFD().sync();
      } catch (IOException e) {
        Log.w(TAG, "同步文件描述符失败：", e);
      }
      fileOutputStream.close();
    }

    @Override
    public void flush() throws IOException {
      fileOutputStream.flush();
    }

    @Override
    public void write(int b) throws IOException {
      fileOutputStream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      fileOutputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      fileOutputStream.write(b, off, len);
    }
  }
}