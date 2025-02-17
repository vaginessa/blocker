package com.merxury.blocker.ui.detail.appinfo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.ifw.util.RuleSerializer
import com.merxury.libkit.utils.FileUtils
import com.merxury.libkit.utils.StorageUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object RuleBackupHelper {
    private val logger = XLog.tag("RuleBackupHelper")

    @Throws(Exception::class)
    suspend fun import(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Uri? {
        return withContext(dispatcher) {
            val savedPath = PreferenceUtil.getSavedRulePath(context) ?: return@withContext null
            val backupName = packageName + Rule.EXTENSION
            val folder = DocumentFile.fromTreeUri(context, savedPath) ?: return@withContext null
            val backupFile = folder.findFile(backupName) ?: run {
                logger.e("Backup file $backupName not found in folder ${folder.uri}")
                return@withContext null
            }
            context.contentResolver.openInputStream(backupFile.uri).use {
                val reader = BufferedReader(InputStreamReader(it))
                val blockerRule = Gson().fromJson(reader, BlockerRule::class.java)
                Rule.import(context, blockerRule)
                logger.i("Import rule ${blockerRule.packageName} from ${backupFile.uri.path} successfully")
            }
            return@withContext backupFile.uri
        }
    }

    @Throws(Exception::class)
    suspend fun export(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean {
        return withContext(dispatcher) {
            val result = Rule.export(context, packageName)
            if (result) {
                logger.i("Export rule $packageName successfully")
            } else {
                logger.e("Export rule $packageName failed")
            }
            return@withContext result
        }
    }

    @Throws(Exception::class)
    suspend fun importIfwRule(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Uri? {
        return withContext(dispatcher) {
            val baseUri = PreferenceUtil.getSavedRulePath(context) ?: return@withContext null
            val baseFolder = DocumentFile.fromTreeUri(context, baseUri) ?: return@withContext null
            val ifwFolder = baseFolder.findFile("ifw")
            val fileName = packageName + Rule.IFW_EXTENSION
            // Find the file in ifw folder
            var backupFile = ifwFolder?.findFile(fileName)
            if (backupFile == null) {
                logger.w("Backup file $fileName not found in folder ${ifwFolder?.uri}")
            }
            // Didn't find rules in ifw folder, try to find rules in root folder
            if (backupFile == null) {
                backupFile = baseFolder.findFile(packageName + Rule.IFW_EXTENSION)
            }
            if (backupFile == null) {
                logger.e("Backup file $fileName not found in folder ${baseFolder.uri}")
                return@withContext null
            }
            val controller = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
            context.contentResolver.openInputStream(backupFile.uri)?.use { stream ->
                val rule = RuleSerializer.deserialize(stream) ?: return@use
                Rule.updateIfwState(rule, controller)
                logger.i("Import ifw rule ${backupFile.uri} success")
            }
            return@withContext backupFile.uri
        }
    }

    @Throws(Exception::class)
    suspend fun exportIfwRule(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? {
        return withContext(dispatcher) {
            val ifwFolder = StorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val ifwFile = files.filter { it.contains(packageName) }
            if (ifwFile.isEmpty()) {
                logger.e("Can't file IFW rule in $ifwFolder, package = $packageName")
                return@withContext null
            }
            ifwFile.forEach {
                logger.i("Export $it")
                val filename = it.split(File.separator).last()
                val content = FileUtils.read(ifwFolder + it)
                val result = StorageUtil.saveIfwToStorage(context, filename, content)
                if (!result) {
                    logger.i("Export $it failed")
                    return@withContext null
                }
            }
            return@withContext PreferenceUtil.getIfwRulePath(context)?.path + File.separator + ifwFile.firstOrNull()
        }
    }
}