package com.example.steambeta.backend.report;

/**
 * DTOs JavaBean compatibles con JRBeanCollectionDataSource para los 4 reportes personales.
 */
public final class PersonalReportDtos {

    private PersonalReportDtos() {}

    public record GeneratedReport(byte[] bytes, String filename, String contentType) {}

    public static final class AcademicActivityRow {
        private final String activityType;
        private final String communityName;
        private final String title;
        private final String statusLabel;
        private final String createdAtLabel;

        public AcademicActivityRow(String activityType, String communityName, String title,
                                   String statusLabel, String createdAtLabel) {
            this.activityType = activityType;
            this.communityName = communityName;
            this.title = title;
            this.statusLabel = statusLabel;
            this.createdAtLabel = createdAtLabel;
        }

        public String getActivityType() { return activityType; }
        public String getCommunityName() { return communityName; }
        public String getTitle() { return title; }
        public String getStatusLabel() { return statusLabel; }
        public String getCreatedAtLabel() { return createdAtLabel; }
    }

    public static final class CommunityMembershipRow {
        private final String name;
        private final String category;
        private final String visibility;
        private final String role;
        private final String membershipStatus;
        private final String joinedAtLabel;
        private final String capacityLabel;

        public CommunityMembershipRow(String name, String category, String visibility, String role,
                                      String membershipStatus, String joinedAtLabel, String capacityLabel) {
            this.name = name;
            this.category = category;
            this.visibility = visibility;
            this.role = role;
            this.membershipStatus = membershipStatus;
            this.joinedAtLabel = joinedAtLabel;
            this.capacityLabel = capacityLabel;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getVisibility() { return visibility; }
        public String getRole() { return role; }
        public String getMembershipStatus() { return membershipStatus; }
        public String getJoinedAtLabel() { return joinedAtLabel; }
        public String getCapacityLabel() { return capacityLabel; }
    }

    public static final class PlanningTaskRow {
        private final String title;
        private final String description;
        private final String projectName;
        private final String statusLabel;
        private final String priorityLabel;
        private final String dueDateLabel;
        private final String createdAtLabel;

        public PlanningTaskRow(String title, String description, String projectName, String statusLabel,
                               String priorityLabel, String dueDateLabel, String createdAtLabel) {
            this.title = title;
            this.description = description;
            this.projectName = projectName;
            this.statusLabel = statusLabel;
            this.priorityLabel = priorityLabel;
            this.dueDateLabel = dueDateLabel;
            this.createdAtLabel = createdAtLabel;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getProjectName() { return projectName; }
        public String getStatusLabel() { return statusLabel; }
        public String getPriorityLabel() { return priorityLabel; }
        public String getDueDateLabel() { return dueDateLabel; }
        public String getCreatedAtLabel() { return createdAtLabel; }
    }

    public static final class ExchangePublicationRow {
        private final String title;
        private final String categoryLabel;
        private final String modalityLabel;
        private final String description;
        private final String statusLabel;
        private final String attachmentsSummary;
        private final String createdAtLabel;

        public ExchangePublicationRow(String title, String categoryLabel, String modalityLabel, String description,
                                      String statusLabel, String attachmentsSummary, String createdAtLabel) {
            this.title = title;
            this.categoryLabel = categoryLabel;
            this.modalityLabel = modalityLabel;
            this.description = description;
            this.statusLabel = statusLabel;
            this.attachmentsSummary = attachmentsSummary;
            this.createdAtLabel = createdAtLabel;
        }

        public String getTitle() { return title; }
        public String getCategoryLabel() { return categoryLabel; }
        public String getModalityLabel() { return modalityLabel; }
        public String getDescription() { return description; }
        public String getStatusLabel() { return statusLabel; }
        public String getAttachmentsSummary() { return attachmentsSummary; }
        public String getCreatedAtLabel() { return createdAtLabel; }
    }
}
