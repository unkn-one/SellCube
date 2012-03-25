package pl.bluex.sellcube.entities;

import javax.persistence.*;
import pl.bluex.sellcube.SellCube;

@Entity
@Table(name = "sellcube_invitedplayer")
public class InvitedPlayer {
    @Id
    private Integer id;
    @ManyToOne
    @JoinColumn(name="adsign_id")
    AdSign adSign;
    @Column(name = "player", nullable = false, length = 255)
    private String player;
    @Column(name = "location_name", length = 255)
    private String locationName;

    public InvitedPlayer() {
    }

    // <editor-fold defaultstate="collapsed" desc="Getters / Setters">
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public AdSign getAdSign() {
        return adSign;
    }

    public void setAdSign(AdSign adSign) {
        this.adSign = adSign;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    // </editor-fold>

    public void save() {
        SellCube.database.save(this);
    }
}