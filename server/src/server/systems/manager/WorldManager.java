package server.systems.manager;

import camera.Focused;
import com.artemis.Component;
import com.artemis.E;
import com.artemis.annotations.Wire;
import entity.character.states.CanWrite;
import entity.npc.OriginPos;
import physics.AOPhysics;
import server.systems.EntityFactorySystem;
import server.systems.ServerSystem;
import shared.model.lobby.Player;
import shared.model.npcs.NPC;
import shared.network.notifications.EntityUpdate.EntityUpdateBuilder;
import shared.network.notifications.RemoveEntity;

import java.util.List;

import static com.artemis.E.E;
import static server.utils.WorldUtils.WorldUtils;

@Wire
public class WorldManager extends DefaultManager {

    private MapManager mapManager;
    private ServerSystem networkManager;
    private SpellManager spellManager;
    private ObjectManager objectManager;
    private EntityFactorySystem entityFactorySystem;

    public void registerEntity(int id) {
        mapManager.updateEntity(id);
    }

    public void registerEntity(int connectionId, int id) {
        networkManager.registerUserConnection(id, connectionId);
        registerEntity(id);
    }

    public void unregisterEntity(int entityId) {
        networkManager.unregisterUserConnection(entityId);
        mapManager.removeEntity(entityId);
        getWorld().delete(entityId);
    }

    void sendEntityRemove(int user, int entity) {
        if (networkManager.playerHasConnection(user)) {
            networkManager
                    .sendTo(networkManager.getConnectionByPlayer(user), new RemoveEntity(entity));
        }
    }

    public void sendEntityUpdate(int user, Object update) {
        if (networkManager.playerHasConnection(user)) {
            networkManager.sendTo(networkManager.getConnectionByPlayer(user), update);
        }
    }

    public void notifyToNearEntities(int entityId, Object update) {
        mapManager.getNearEntities(entityId).forEach(nearPlayer -> {
            sendEntityUpdate(nearPlayer, update);
        });
    }

    public void notifyUpdate(int entityId, Object update) {
        sendEntityUpdate(entityId, update);
        notifyToNearEntities(entityId, update);
    }

    public void entityDie(int entityId) {

        final E e = E(entityId);
        if (e.hasNPC()) {
            int npcId = e.nPCId();
            NPC npc = world.getSystem(NPCManager.class).getNpcs().get(npcId);
            // TODO check if should respawn

            OriginPos originPos = e.getOriginPos();
            int npcRespawn = world.create();
            E(npcRespawn)
                    .respawnTime(5)
                    .respawnNpcId(npcId)
                    .respawnPos(originPos);

            unregisterEntity(e.id());
        } else {
            // RESET USER. TODO implement ghost
            // reset health
            e.getHealth().min = e.getHealth().max;
            // reset mana
            EntityUpdateBuilder resetUpdate = EntityUpdateBuilder.of(entityId);
            resetUpdate.withComponents(e.getHealth());
            if (e.hasMana()) {
                e.getMana().min = e.getMana().max;
                resetUpdate.withComponents(e.getMana());
            }
            sendEntityUpdate(entityId, resetUpdate.build());
            notifyUpdate(entityId, EntityUpdateBuilder.of(entityId).withComponents(e.getWorldPos()).build());
        }
    }

    public void login(int connectionId, Player player) {
        final int entity = entityFactorySystem.createPlayer(player.getPlayerName(), player.getHero(), player.getTeam());
        List<Component> components = WorldUtils(getWorld()).getComponents(getWorld().getEntity(entity));
        components.add(new Focused());
        components.add(new AOPhysics());
        components.add(new CanWrite());
        networkManager.sendTo(connectionId, EntityUpdateBuilder.of(entity).withComponents(components.toArray(new Component[0])).build());
        registerEntity(connectionId, entity);
    }
}
