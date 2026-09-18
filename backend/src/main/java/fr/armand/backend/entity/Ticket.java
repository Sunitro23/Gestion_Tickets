package fr.armand.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "OUVERTE";

    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // Constructeur utilisé par JPA pour relire une ligne de la base.
    protected Ticket() {
    }

    public Ticket(String title, String description, AppUser requester, Category category) {
        this.title = title;
        this.description = description;
        this.requester = requester;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public AppUser getRequester() {
        return requester;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public Category getCategory() {
        return category;
    }

    public String getResolution() {
        return resolution;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    // JPA appelle cette méthode avant une modification de la ligne du ticket.
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
