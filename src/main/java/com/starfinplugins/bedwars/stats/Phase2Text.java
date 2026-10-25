package com.starfinplugins.bedwars.stats;

import java.util.HashMap;
import java.util.Map;

/** Initial translations for the phase 2 screens, using the existing language preference. */
public final class Phase2Text {
    private static final Map<String, String[]> WORDS = new HashMap<String, String[]>();
    static {
        put("profile", "Perfil BedWars", "BedWars Profile", "Perfil BedWars");
        put("progress", "Progressao", "Progress", "Progreso");
        put("rewards", "Recompensas", "Rewards", "Recompensas");
        put("ranking", "Ranking BedWars", "BedWars Ranking", "Ranking BedWars");
        put("level", "Nivel", "Level", "Nivel");
        put("total_xp", "XP total", "Total XP", "XP total");
        put("last_match_xp", "XP da ultima partida", "Last match XP", "XP de la ultima partida");
        put("next_level", "Para o proximo nivel", "To next level", "Para el siguiente nivel");
        put("wins", "Vitorias", "Wins", "Victorias");
        put("losses", "Derrotas", "Losses", "Derrotas");
        put("kills", "Abates", "Kills", "Bajas");
        put("final_kills", "Abates finais", "Final kills", "Bajas finales");
        put("beds", "Camas destruidas", "Beds destroyed", "Camas destruidas");
        put("games", "Partidas", "Games", "Partidas");
        put("win_rate", "Taxa de vitorias", "Win rate", "Tasa de victorias");
        put("selected_title", "Titulo selecionado", "Selected title", "Titulo seleccionado");
        put("locked", "Bloqueado", "Locked", "Bloqueado");
        put("unlocked", "Desbloqueado", "Unlocked", "Desbloqueado");
        put("select", "Clique para selecionar", "Click to select", "Clic para seleccionar");
        put("required_level", "Nivel necessario", "Required level", "Nivel necesario");
        put("previous", "Pagina anterior", "Previous page", "Pagina anterior");
        put("next", "Proxima pagina", "Next page", "Pagina siguiente");
        put("close", "Fechar", "Close", "Cerrar");
        put("position", "Posicao", "Position", "Posicion");
        put("your_position", "Sua posicao", "Your position", "Tu posicion");
        put("loading", "Carregando dados...", "Loading data...", "Cargando datos...");
        put("unavailable", "Dados indisponiveis", "Data unavailable", "Datos no disponibles");
        put("mode", "Modalidade", "Mode", "Modalidad");
        put("period", "Periodo", "Period", "Periodo");
        put("metric", "Metrica", "Metric", "Metrica");
        put("all", "Geral", "All time", "General");
        put("weekly", "Semanal", "Weekly", "Semanal");
        put("monthly", "Mensal", "Monthly", "Mensual");
        put("none", "Nenhum", "None", "Ninguno");
        put("title_unlocked", "Titulo desbloqueado", "Title unlocked", "Titulo desbloqueado");
        put("title_selected", "Titulo selecionado", "Title selected", "Titulo seleccionado");
        put("title_blocked", "Titulo bloqueado ou MySQL indisponivel", "Title locked or MySQL unavailable", "Titulo bloqueado o MySQL no disponible");
        put("match_xp", "XP da partida", "Match XP", "XP de partida");
    }
    private Phase2Text() { }
    private static void put(String key, String pt, String en, String es) {
        WORDS.put(key, new String[] {pt, en, es});
    }
    public static String text(String language, String key) {
        String[] values = WORDS.get(key);
        if (values == null) return key;
        if ("en_US".equals(language)) return values[1];
        if ("es_ES".equals(language)) return values[2];
        return values[0];
    }
    public static String metric(String language, RankingSpec.Metric metric) {
        switch (metric) {
            case WINS: return text(language, "wins");
            case GAMES: return text(language, "games");
            case KILLS: return text(language, "kills");
            case FINAL_KILLS: return text(language, "final_kills");
            case DEATHS: return "en_US".equals(language) ? "Deaths" : "es_ES".equals(language) ? "Muertes" : "Mortes";
            case FINAL_DEATHS: return "en_US".equals(language) ? "Final deaths" : "es_ES".equals(language) ? "Muertes finales" : "Mortes finais";
            case BEDS: return text(language, "beds");
            case XP: return text(language, "total_xp");
            case LEVEL: return text(language, "level");
            case WIN_RATE: return text(language, "win_rate");
            case KD: return "K/D";
            default: return metric.name();
        }
    }
    public static String period(String language, RankingSpec.Period period) {
        return text(language, period == RankingSpec.Period.WEEKLY ? "weekly"
            : period == RankingSpec.Period.MONTHLY ? "monthly" : "all");
    }
}
