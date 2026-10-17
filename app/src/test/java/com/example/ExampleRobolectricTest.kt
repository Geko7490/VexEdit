package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AspectRatio
import com.example.data.Project
import com.example.data.ProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VexEdit Studio", appName)
  }

  @Test
  fun `verify project creation and persistence lifecycle`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ProjectRepository(context)

    // Create a new real project
    val newProject = Project(
      title = "Cyberpunk Reel",
      aspectRatio = AspectRatio.RATIO_9_16
    )
    repo.saveProject(newProject)

    val savedList = repo.projects.value
    val found = savedList.find { it.id == newProject.id }
    assertNotNull(found)
    assertEquals("Cyberpunk Reel", found?.title)

    // Rename project
    repo.renameProject(newProject.id, "Cyberpunk Reel V2")
    val renamed = repo.projects.value.find { it.id == newProject.id }
    assertEquals("Cyberpunk Reel V2", renamed?.title)

    // Duplicate project
    val duplicated = repo.duplicateProject(newProject.id)
    assertNotNull(duplicated)
    assertEquals("Cyberpunk Reel V2 (Copy)", duplicated?.title)

    // Delete project
    repo.deleteProject(newProject.id)
    assertNull(repo.projects.value.find { it.id == newProject.id })
  }
}
