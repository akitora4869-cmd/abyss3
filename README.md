# Abyssal Arcanum v0.5.1 — development source
Minecraft 1.20.1 / Forge 47.3.0 / Java 17.

## Added
- Skill-tree screen: press K or `/arcana tree`, displays level, SP, 3 learnable magic nodes, sends server-side learn requests. Currently a simple node menu, not a branching scrollable tree.
- Variant mob material drops: variant_essence, elemental_core, corrupted_essence, abyss_heart (tagged AbyssalBoss only).
- Basic variant selection for zombie, skeleton, spider, enderman on entity join (6% total) with boosted HP and attack; this is a prototype, NOT a spawn-reason-aware AI implementation.
- Recipes updated to consume variant/elemental/corrupted materials. Abyss shard now requires a boss heart plus elemental/corrupted drops.
- Placeholder item models and ja_jp/en_us names.

## Limitations
- Not compiled or tested in Forge. No Gradle wrapper included.
- GUI uses three nodes; tree dependencies, multi-rank and drag/pan are future work.
- Spawn variant tagging may reroll on entity reload when a nonvariant mob was never marked; current spawn hook is a prototype and may affect command-spawned mobs.
- Warden boss tag comes from the previous prototype. Dedicated EntityType and altar world generation remain pending.
- The old recipes and encounter systems have not been end-to-end validated.

## Build
Use a Forge MDK Gradle wrapper compatible with ForgeGradle 6 or Gradle installed locally; run `gradle build`.

## Terminology update v0.5.1
- 狂暴種 / 亜種 -> 狂化種 (berserk) / material: 狂化種の精髄
- 属性種 -> 適応種 (adaptive) / material: 適応種の属性核
- 深淵侵食種 -> 侵食種 (invasive) / material: 侵食種の魂
- Boss category 深淵種 remains unchanged; material 深淵の心臓 remains unchanged.
- Registry IDs (`variant_essence`, `elemental_core`, `corrupted_essence`) deliberately remain unchanged for save compatibility. All existing crafting recipes reference those IDs and therefore automatically use the renamed materials.
- Existing mobs tagged with the old variant identifiers remain eligible for their drops.

## v0.6.0: 変異Mobの戦闘AI・属性魔法（試作）
- 狂化種: 4～16ブロックで対象に突進（約4.5秒間隔）。
- 適応種: 炎・氷・雷をスポーン時に抽選してNBTに保存。射程16ブロック、5秒間隔で魔法攻撃。炎は炎上、氷は凍結・鈍足、雷はノックバック。
- 侵食種: 紫オーラ、背後への短距離転移、周囲4ブロックの魔力衝撃波（7秒間隔）。
- 戦闘処理はサーバー側。近隣32ブロック内を10tickごとに検索し、同一Mobの同一tick重複処理を防止。ブロック破壊なし。
- 既存の狂化種・適応種・侵食種の素材ドロップおよびレシピは維持。

注意: Forge環境でのコンパイル・実機動作は未検証です。既存のv0.5.1自体にも未検証コードがあります。テストワールドで確認してください。

## v0.7.0: 近接アクティブスキル（試作）
- 飛燕撃 (`/arcana learn dive`, `/arcana cast dive`): Lv3/SP1。地上で使用し視線の水平方向に飛び込む。着地時半径2.5ブロック以内の敵に3ダメージ。CT5秒。最大25tickの判定猶予。落下ダメージ無効化はなし。
- 扇閃 (`/arcana learn sweep`, `/arcana cast sweep`): Lv6/SP1。前方120度、半径4.5ブロックの敵に5ダメージと軽いノックバック。CT8秒。遮蔽物越しには当たらない。
- Kキーの既存ツリー画面にも習得ボタンを暫定追加。**追加2スキルの習得状態はGUIパケットに未反映**（習得後もボタンが表示されます）。コマンド側は重複習得を防止します。
- 専用の発動キー、専用アニメーション、スキルランク、武器種制限は未実装。
- Gradleビルドと実機動作は未検証です。


## v0.8.0 Ability upgrade
- K tree: Strength / Arcane rank (max 30, 1 SP per rank).
- Strength +3% physical damage/rank; Arcane +4% magic damage/rank.
- Mob tiers auto-scale: berserk 2, adaptive 4, invasive 8; boss tag support provisional.
- `/arcana upgrade strength` and `/arcana upgrade arcane`.
- Stat ranks are persisted in existing player NBT and preserved on death.
- Forge compilation/gameplay verification not performed.


## v0.9.0 能力ツリー拡張
- 体力: 1SPで最大HP +2 (上限30段階)
- 防御力: 1SPで防具値 +0.5 (上限30段階)
- 敏捷性: 1SPで基本移動速度 +0.5% (上限30段階)
- 最大MP: 1SPで最大MP +10 (上限30段階)
- Kキーのスキルツリーから6能力を強化。`/arcana upgrade vitality|defense|agility|mana` も利用可能。
- 能力補正は一時的なAttributeModifierで適用し、ログイン後も保存済みランクから復元。
- 敵の攻撃補正は既存のPowerStatsの種別ランクを維持。防御等の敵能力拡張は未実装。
- 注意: Forgeのコンパイル・実機検証は未実施。


## v1.0.0 Mob balance
- Mobs do not use the player's six-stat upgrade tree.
- Zombie/Skeleton/Spider/Enderman have fixed health, damage and armor by normal/berserk/adaptive/invasive tier.
- Existing entities are normalized once on their next load using ArcanaBalanceV1; values are not repeatedly multiplied.
- Dedicated abyss bosses retain their own HP and damage configuration.
- This is an initial tuning pass; build and in-game balance have not been verified.


## v1.1.0 属性弱点・適応耐性
- 適応種は `ArcanaElement` に保存された属性へのダメージを **20%** に軽減（80%軽減）。
- 弱点属性は受けるダメージを **175%** に増幅。その他の属性は等倍。
- 対応：火↔氷、雷→地、風→雷、地→風、光↔闇、深淵→光（左が適応属性、右が弱点属性）。
- 侵食種は深淵適応扱い。深淵ダメージを20%に軽減し、光属性に175%の弱点を持つ。
- レーヴ（旧fireball）・氷弾・雷撃、適応種の属性攻撃、侵食種の衝撃波を属性DamageType化。
- 通常の物理攻撃・落下・環境ダメージには属性補正を適用しません。
- 将来の風・地・光・闇・深淵のプレイヤー魔法は DamageType を用意済みですが魔法そのものは未実装です。
- 弱点による防御低下は実効被ダメージの増幅で表現し、Armor属性値そのものは変更しません。
- 既存の保存済み適応種の `ArcanaElement` が空の場合は、既存の戦闘AIが属性を補充します。
- 注意：コンパイル・実機動作は未検証です。


## v1.1.1: 深淵属性の特殊相性
- 深淵属性ダメージは、深淵耐性がないすべてのLivingEntity（プレイヤーを含む）に **1.75倍**。
- 深淵耐性がある相手には **0.05倍（95%軽減）**。
- 侵食種（invasive / 旧corrupted）および `AbyssalBoss` タグを持つ深淵種ボスは標準で深淵耐性。
- 他の個体にも `ArcanaAbyssResistance` (persistent boolean) で明示的に耐性を付与可能。
- 光属性が侵食種・深淵種の弱点（1.75倍）である既存仕様を維持。
- 属性補正は `LivingHurtEvent` において処理し、後続の防具や他Modの軽減処理により最終値は変化する場合がある。
- 本変更はMod独自の `spell_abyss` ダメージタイプに適用される。通常の近接攻撃やバニラのダメージは対象外。


## v1.1.2: Forge 1.20.1 compile API fixes
- Correct Player creative check inside LivingEntity predicate.
- Replace unavailable ServerPlayer.setTimeOffset with per-player ClientboundSetTimePacket; restore real server time when leaving arena.
- Use Screen.renderBackground(GuiGraphics) signature.
- Create custom DamageSource from registered DamageType holder, not private DamageSources.source method.
- Build has not been executed in this environment; verify via GitHub Actions.


## v1.1.3 compile fix
- Rename the nested pattern variable in VariantCombat.runCombat from `player` to `nearbyPlayer` to avoid Java variable shadowing.
