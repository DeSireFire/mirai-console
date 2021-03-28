/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */


package net.mamoe.mirai.console.intellij.creator.build

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.writeChild
import net.mamoe.mirai.console.intellij.assets.FT
import net.mamoe.mirai.console.intellij.creator.MiraiProjectModel
import net.mamoe.mirai.console.intellij.creator.tasks.getTemplate
import net.mamoe.mirai.console.intellij.creator.tasks.invokeAndWait
import net.mamoe.mirai.console.intellij.creator.tasks.runWriteActionAndWait
import net.mamoe.mirai.console.intellij.creator.tasks.writeChild
import net.mamoe.mirai.console.intellij.creator.templateProperties
import org.jetbrains.kotlin.idea.core.util.toPsiFile

sealed class ProjectCreator(
    val module: Module,
    val root: VirtualFile,
    val model: MiraiProjectModel,
) {
    val project get() = module.project

    init {
        model.checkValuesNotNull()
    }

    protected val filesChanged = mutableListOf<VirtualFile>()

    @Synchronized
    protected fun addFileChanged(vf: VirtualFile) {
        filesChanged.add(vf)
    }

    fun doFinish(indicator: ProgressIndicator) {
        indicator.text2 = "Reformatting files"
        invokeAndWait {
            for (file in filesChanged) {
                val psi = file.toPsiFile(project) ?: continue
                ReformatCodeProcessor(psi, false).run()
            }
        }
    }

    abstract fun createProject(
        module: Module,
        root: VirtualFile,
        model: MiraiProjectModel,
    )
}

sealed class GradleProjectCreator(
    module: Module, root: VirtualFile, model: MiraiProjectModel,
    private val templateBuildGradle: String,
    private val templateSettingsGradle: String,
) : ProjectCreator(module, root, model) {

    protected inner class TemplatesImpl {
        val buildGradle: String
            get() = project.getTemplate(
                templateBuildGradle,
                model.templateProperties
            )

        val settingsGradle: String
            get() = project.getTemplate(
                templateSettingsGradle,
                model.templateProperties
            )
    }

    protected val templates: TemplatesImpl = TemplatesImpl()
}

class GradleKotlinProjectCreator(
    module: Module, root: VirtualFile, model: MiraiProjectModel,
) : GradleProjectCreator(
    module, root, model,
    FT.BuildGradleKts,
    FT.SettingsGradleKts,
) {
    override fun createProject(module: Module, root: VirtualFile, model: MiraiProjectModel) {
        runWriteActionAndWait {
            VfsUtil.createDirectoryIfMissing(root, "src/main/${model.languageType.sourceSetDirName}")
            VfsUtil.createDirectoryIfMissing(root, "src/main/resources")
            filesChanged += root.writeChild("build.gradle.kts", templates.buildGradle)
            filesChanged += root.writeChild("settings.gradle.kts", templates.settingsGradle)
            filesChanged += root.writeChild(model.languageType.pluginMainClassFile(this))
            filesChanged += root.writeChild("src/main/resources/META-INF/services/net.mamoe.mirai.console.plugin.jvm.JvmPlugin", model.mainClassQualifiedName)
        }
    }
}

class GradleGroovyProjectCreator(
    module: Module, root: VirtualFile, model: MiraiProjectModel,
) : GradleProjectCreator(
    module, root, model,
    FT.BuildGradle,
    FT.SettingsGradle,
) {
    override fun createProject(module: Module, root: VirtualFile, model: MiraiProjectModel) {
        TODO("Not yet implemented")
    }
}