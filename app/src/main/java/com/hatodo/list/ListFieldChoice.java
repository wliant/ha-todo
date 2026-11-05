package com.hatodo.list;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One selectable cost on a FIXED field - "30 min", worth 50 points. */
@Entity
@Table(name = "list_field_choice")
public class ListFieldChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_field_id", nullable = false)
    private ListField field;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private short position;

    protected ListFieldChoice() {
        // for JPA
    }

    public ListFieldChoice(ListField field, String label, int points, short position) {
        this.field = field;
        this.label = label;
        this.points = points;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public ListField getField() {
        return field;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public short getPosition() {
        return position;
    }

    public void setPosition(short position) {
        this.position = position;
    }
}
