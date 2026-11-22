package com.example.steambeta.backend.task;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/tasks")
public class AcademicTaskController {
    private final AcademicTaskService taskService;

    public AcademicTaskController(AcademicTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<AcademicTaskService.TaskItem> myTasks(@RequestParam(required = false) String status) {
        return taskService.myTasks(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicTaskService.TaskItem createTask(@RequestBody AcademicTaskService.TaskDraft draft) {
        return taskService.createTask(draft);
    }

    @PatchMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTask(@PathVariable UUID taskId, @RequestBody AcademicTaskService.TaskUpdate update) {
        taskService.updateTask(taskId, update);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable UUID taskId) {
        taskService.deleteTask(taskId);
    }
}
