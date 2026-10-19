package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.UploadJob

internal fun homeUploadSummaryJob(jobs: List<UploadJob>): UploadJob? =
    jobs.firstOrNull {
        it.state == com.example.jetsoncontroller.model.UploadJobState.QUEUED ||
            it.state == com.example.jetsoncontroller.model.UploadJobState.SCANNING ||
            it.state == com.example.jetsoncontroller.model.UploadJobState.UPLOADING
    } ?: jobs.firstOrNull {
        it.state == com.example.jetsoncontroller.model.UploadJobState.COMPLETED ||
            it.state == com.example.jetsoncontroller.model.UploadJobState.FAILED
    }
