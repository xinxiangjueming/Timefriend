package com.example.timefriend.data

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Zstandard 压缩工具（[com.github.luben.zstd] jni 绑定）。
 *
 * 选用 zstd 而非 gzip/zip：压缩率更高；且 zstd 帧内不含时间戳，
 * 同输入 + 同档位 + 同库版本必得完全相同字节——这使"库没变→压缩产物 MD5 一致
 * →可跳过重复上传"成为可能（与 sportlink BackupManager 的设计一致）。
 */
object ZstdUtil {

    /** 将 [src] 压缩为 zstd 写入 [dst]；[level] 1..22，备份取 12 在体积与耗时间平衡。 */
    fun compress(src: File, dst: File, level: Int = 12) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                ZstdOutputStream(output, level).use { zout -> input.copyTo(zout) }
            }
        }
    }

    /** 将 zstd 压缩的 [src] 解压写入 [dst]。 */
    fun decompress(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                ZstdInputStream(input).use { zin -> zin.copyTo(output) }
            }
        }
    }
}
