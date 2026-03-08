# 🛡 SecurePlots

Mod de Fabric para Minecraft 1.21.1 que permite a los jugadores proteger zonas del mundo usando bloques especiales. Incluye sistema de permisos por miembro, grupos, flags globales, teleporte, vuelo y menú visual completo.

---

## 📋 Requisitos

| Requisito | Versión |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.4 o superior |
| Fabric API | 0.116.9+1.21.1 o superior |
| Java | 21 |

---

## 📦 Instalación

1. Instalá [Fabric Loader](https://fabricmc.net/use/) para Minecraft 1.21.1.
2. Descargá [Fabric API](https://modrinth.com/mod/fabric-api) y colocalo en la carpeta `mods/`.
3. Colocá el archivo `.jar` de SecurePlots también en la carpeta `mods/`.
4. Iniciá el servidor o cliente. El archivo de configuración se genera automáticamente en `config/secure_plots.json`.

---

## 🚀 Cómo empezar

1. Obtené un **Bloque de Parcela** (aparece en el grupo funcional del inventario creativo).
2. Colocalo en el mundo. Ese bloque se convierte en el centro de tu protección.
3. Usá el **Plot Blueprint** (ítem especial) o hacé clic derecho sobre el bloque para abrir el menú de gestión.

---

## 🧱 Bloques de Parcela

Hay 5 niveles de protección. Cada bloque define el radio de la zona protegida:

| Bloque | Nivel | Radio | Luminosidad |
|---|---|---|---|
| Bronze Plot Block | Bronce | 15×15 bloques | 4 |
| Gold Plot Block | Oro | 30×30 bloques | 5 |
| Emerald Plot Block | Esmeralda | 50×50 bloques | 6 |
| Diamond Plot Block | Diamante | 75×75 bloques | 7 |
| Netherite Plot Block | Netherita | 100×100 bloques | 8 |

Todos tienen dureza 50 y resistencia a explosiones 1200.

---

## ⬆️ Mejoras de nivel

Para subir de nivel, abrí el menú de la parcela y andá a la pestaña **Mejorar**. Los costos por defecto son:

| De → A | Materiales |
|---|---|
| Bronce → Oro | 15 bloques de oro |
| Oro → Esmeralda | 10 bloques de esmeralda |
| Esmeralda → Diamante | 64 diamantes |
| Diamante → Netherita | 1 bloque de netherita |

Los costos son configurables en `secure_plots.json`.

---

## 🗂️ Menú de gestión

Se abre usando el **Plot Blueprint** dentro de tu parcela o haciendo clic derecho en el bloque. Tiene 4 pestañas:

### 📋 Info
- Nombre de la parcela, dueño, nivel, tamaño y coordenadas.
- Tu rol actual.
- Botón de teleporte (si el TP público está activo, o si sos owner/admin).
- Botón para renombrar (solo owner).
- Indicador de inactividad (si está habilitado en config).

### 👥 Miembros
- Lista de todos los miembros con su rol y grupos asignados.
- Clic en un miembro para editar sus permisos individuales.
- Shift+clic para remover un miembro.
- Botón para agregar nuevos miembros (requiere permiso `MANAGE_MEMBERS`).

### 🌐 Permisos Globales
- Toggles de todos los permisos globales (flags) que afectan a todos los jugadores en la parcela.
- Sección de grupos: crear, editar y eliminar grupos de permisos.

### ⬆️ Mejorar
- Muestra el nivel actual y el siguiente.
- Lista los materiales requeridos con indicador de si los tenés o no.
- Botón para mejorar (consume los materiales automáticamente).

---

## 👤 Roles

| Rol | Descripción |
|---|---|
| **OWNER** | Dueño de la parcela. Todos los permisos. |
| **ADMIN** | Puede gestionar miembros, permisos, construir, interactuar y usar TP. |
| **MEMBER** | Puede construir, interactuar, abrir contenedores y usar TP. |
| **VISITOR** | Solo puede interactuar y entrar (por defecto para cualquier jugador sin acceso). |

---

## 🔑 Permisos individuales

Estos permisos se pueden asignar por miembro o por grupo:

| Permiso | Descripción |
|---|---|
| `BUILD` | Colocar y romper bloques |
| `INTERACT` | Palancas, puertas, botones |
| `CONTAINERS` | Abrir cofres e inventarios |
| `PVP` | Atacar a otros jugadores |
| `MANAGE_MEMBERS` | Agregar y remover miembros |
| `MANAGE_PERMS` | Cambiar permisos de miembros |
| `MANAGE_FLAGS` | Cambiar permisos globales |
| `MANAGE_GROUPS` | Crear y editar grupos |
| `TP` | Usar `/sp tp` para llegar a la parcela |
| `FLY` | Volar dentro de la parcela |
| `ENTER` | Entrar al área de la parcela |

---

## 🌐 Permisos Globales (Flags)

Afectan a **todos** los jugadores que estén en la parcela, incluidos visitantes:

| Flag | Descripción |
|---|---|
| `ALLOW_VISITOR_BUILD` | Cualquiera puede construir |
| `ALLOW_VISITOR_INTERACT` | Cualquiera puede interactuar |
| `ALLOW_VISITOR_CONTAINERS` | Cualquiera puede abrir cofres |
| `ALLOW_PVP` | PvP habilitado para todos |
| `ALLOW_FLY` | Todos pueden volar en la parcela |
| `ALLOW_TP` | Todos pueden hacer `/sp tp` a esta parcela |
| `GREETINGS` | Mostrar mensaje en el HUD al entrar/salir |

Por defecto, las nuevas parcelas tienen `ALLOW_TP` y `GREETINGS` activados.

---

## 👥 Grupos de permisos

Los grupos permiten asignar un conjunto de permisos a varios miembros a la vez. Se crean desde la pestaña **Permisos Globales** del menú o con el comando `/sp group create <nombre>`. Cada grupo tiene sus propios permisos y lista de miembros.

---

## ✈️ Sistema de vuelo

Si el flag `ALLOW_FLY` está activo en una parcela, todos los jugadores dentro podrán volar. Si solo se quiere dar vuelo a miembros específicos, se puede activar el permiso `FLY` individualmente desde el menú de permisos del miembro. El vuelo se activa automáticamente al entrar y se revoca al salir (sin afectar a jugadores en creativo o con vuelo propio).

---

## 🗺️ Plot Blueprint

Ítem especial para gestionar tus parcelas:

- **Clic normal** dentro de una parcela: abre el menú de gestión.
- **Clic normal** fuera de una parcela: abre la lista de todas tus parcelas con opciones de TP.
- **Shift+clic**: muestra el borde visual de la protección más cercana.

---

## 💬 Comandos

Todos los comandos funcionan con `/sp` o `/secureplots`.

### Generales

| Comando | Descripción |
|---|---|
| `/sp list` | Lista todas tus protecciones con coordenadas y nivel. |
| `/sp info [parcela]` | Muestra información detallada de la parcela donde estás o la indicada. |
| `/sp view` | Muestra el borde visual de tu protección más cercana. |
| `/sp rename <nombre>` | Renombra la parcela donde estás parado. |
| `/sp tp [parcela]` | Te teleporta a una de tus parcelas o a una pública. |

### Miembros

| Comando | Descripción |
|---|---|
| `/sp add <jugador> <parcela\|all>` | Agrega un jugador como miembro. |
| `/sp remove <jugador> <parcela\|all>` | Remueve a un miembro. |

### Permisos y Flags

| Comando | Descripción |
|---|---|
| `/sp flag` | Lista todas las flags disponibles. |
| `/sp flag <flag>` | Muestra el estado de una flag en la parcela actual. |
| `/sp flag <flag> <true\|false> [parcela]` | Activa o desactiva una flag. |
| `/sp perm` | Lista todos los permisos disponibles. |
| `/sp perm <jugador> <permiso>` | Muestra si un miembro tiene un permiso. |
| `/sp perm <jugador> <permiso> <true\|false> [parcela]` | Cambia un permiso individual. |
| `/sp fly [true\|false] [parcela]` | Activa/desactiva el vuelo global + permiso FLY para todos los miembros. |

### Grupos

| Comando | Descripción |
|---|---|
| `/sp group` | Lista los grupos de la parcela actual. |
| `/sp group create <nombre>` | Crea un grupo de permisos. |
| `/sp group delete <nombre>` | Elimina un grupo. |
| `/sp group addmember <grupo> <jugador>` | Agrega un miembro al grupo. |
| `/sp group removemember <grupo> <jugador>` | Quita un miembro del grupo. |
| `/sp group setperm <grupo> <permiso> <true\|false>` | Activa o desactiva un permiso en el grupo. |

### Argumento `<parcela>`
Se puede pasar el **nombre** de la parcela, su **número** según `/sp list`, o `all` para aplicar a todas.

---

## ⚙️ Configuración

El archivo `config/secure_plots.json` se genera automáticamente. Opciones disponibles:

```json
{
  "maxPlotsPerPlayer": 3,
  "inactivityExpiry": {
    "enabled": false,
    "baseDays": 45,
    "daysPerTier": 5
  },
  "upgradeCosts": [
    { "fromTier": 0, "toTier": 1, "items": [{ "itemId": "minecraft:gold_block", "amount": 15 }] },
    { "fromTier": 1, "toTier": 2, "items": [{ "itemId": "minecraft:emerald_block", "amount": 10 }] },
    { "fromTier": 2, "toTier": 3, "items": [{ "itemId": "minecraft:diamond", "amount": 64 }] },
    { "fromTier": 3, "toTier": 4, "items": [{ "itemId": "minecraft:netherite_block", "amount": 1 }] }
  ]
}
```

| Opción | Descripción |
|---|---|
| `maxPlotsPerPlayer` | Máximo de parcelas por jugador. `0` = ilimitado. |
| `inactivityExpiry.enabled` | Si está en `true`, las protecciones expiran si el dueño no entra al servidor. |
| `inactivityExpiry.baseDays` | Días base antes de que expire una protección. |
| `inactivityExpiry.daysPerTier` | Días extra de gracia por cada nivel de mejora. |
| `upgradeCosts` | Lista de costos de mejora. Se pueden usar cualquier ítem de Minecraft. |

---

## 🔧 Permisos de administrador

Los operadores del servidor con el tag `plot_admin` (asignable con `/tag <jugador> add plot_admin`) tienen acceso completo a todas las parcelas como si fueran dueños. Pueden gestionar miembros, permisos, flags y grupos de cualquier parcela.

---

## 💾 Datos

Los datos de las parcelas se guardan como `PersistentState` del mundo (en `world/data/`). No requieren base de datos externa. Son retrocompatibles: parcelas guardadas sin flags o grupos se cargan con valores por defecto.
