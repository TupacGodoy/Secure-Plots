# Protected Areas Feature

## Completed Features

- [x] Comando para jugadores: `/sp protectedarea list` - Ver áreas públicas
- [x] Sistema de permisos por grupo para áreas protegidas (LuckPerms integration)
- [x] Protección de entidades dentro de áreas (EntityProtectionMixin)
- [x] Protección de explosiones (TNT, creepers) (ExplosionMixin)
- [x] Área protegida temporal (expira después de X tiempo)
- [x] Notificación al entrar/salir de área protegida (ProtectedAreaTracker)
- [x] Holograma mostrando límites de área protegida (ProtectedAreaHologram)
- [x] Protección de líquidos (lava/agua) (LiquidFlowMixin)

## Implementation Notes

All protected area features were implemented in version 1.0. The system includes:
- ProtectedAreaManager for persistent area storage
- ProtectedAreaTracker for enter/exit detection and notifications
- ProtectedAreaHologram for boundary visualization
- Mixins for entity, explosion, and liquid protection
- LuckPerms group-based permission support
- Temporary areas with automatic expiry
