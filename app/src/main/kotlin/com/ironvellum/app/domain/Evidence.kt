package com.ironvellum.app.domain

/**
 * Every research citation the program generator, the templates and the
 * improve pass lean on, in one place so a `why` string and a KDoc can never
 * drift from the paper behind them.
 *
 * Entries carry only what the evidence brief (D:/Monarch/.tmp/evidence-brief.md)
 * marks verified: each DOI was checked against the Crossref registry, and the
 * retracted Barbalho single-joint studies are deliberately absent - rule 19
 * forbids citing them. [label] is the short form used inside user-facing
 * `why` lines and the preset notes; the long form lives in the constructor.
 */
enum class Evidence(val label: String, val citation: String, val doi: String) {
    PELLAND_2026(
        "Pelland 2026",
        "Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC (2026). The Resistance Training Dose Response. Sports Medicine 56(2):481-505.",
        "10.1007/s40279-025-02344-w",
    ),
    SCHONFELD_VOL_2017(
        "Schoenfeld 2017",
        "Schoenfeld BJ, Ogborn D, Krieger JW (2017). Dose-response relationship between weekly resistance training volume and increases in muscle mass. J Sports Sciences 35(11):1073-82.",
        "10.1080/02640414.2016.1210197",
    ),
    RALSTON_2017(
        "Ralston 2017",
        "Ralston GW, Kilgore L, Wyatt FB, Baker JS (2017). The Effect of Weekly Set Volume on Strength Gain: A Meta-Analysis. Sports Medicine 47(12):2585-2601.",
        "10.1007/s40279-017-0762-7",
    ),
    ACSM_2009(
        "ACSM 2009",
        "ACSM Position Stand (2009). Progression Models in Resistance Training for Healthy Adults. Med Sci Sports Exerc 41(3):687-708.",
        "10.1249/MSS.0b013e3181915670",
    ),
    // ACSM 2026 position stand: the brief marks its DOI UNVERIFIED (full text
    // paywalled at fetch time), so it is deliberately not citable here.
    GRGIC_2018(
        "Grgic 2018",
        "Grgic J, Schoenfeld BJ, Davies TB et al. (2018). Effect of Resistance Training Frequency on Gains in Muscular Strength. Sports Medicine 48:1207-1220.",
        "10.1007/s40279-018-0872-x",
    ),
    ROBINSON_2024(
        "Robinson 2024",
        "Robinson ZP, Pelland JC, Remmert JF, Refalo MC, Jukic I, Steele J, Zourdos MC (2024). Exploring the Dose-Response Relationship Between Estimated Resistance Training Proximity to Failure, Strength Gain, and Muscle Hypertrophy. Sports Medicine 54(9):2209-2231.",
        "10.1007/s40279-024-02069-2",
    ),
    REFALO_2023(
        "Refalo 2023",
        "Refalo MC, Helms ER, Trexler ET, Hamilton DL, Fyfe JJ (2023). Influence of Resistance Training Proximity-to-Failure on Skeletal Muscle Hypertrophy. Sports Medicine 53(3):649-665.",
        "10.1007/s40279-022-01784-y",
    ),
    LOPEZ_2021(
        "Lopez 2021",
        "Lopez P, Radaelli R, Taaffe DR et al. (2021). Resistance Training Load Effects on Muscle Hypertrophy and Strength Gain. Med Sci Sports Exerc 53(6):1206-1216.",
        "10.1249/MSS.0000000000002585",
    ),
    SINGER_2024(
        "Singer 2024",
        "Singer A, Wolf M, Generoso L et al. (2024). Give it a rest: a systematic review with Bayesian meta-analysis on the effect of inter-set rest interval duration on muscle hypertrophy. Frontiers in Sports and Active Living 6:1429789.",
        "10.3389/fspor.2024.1429789",
    ),
    SCHONFELD_REST_2016(
        "Schoenfeld 2016",
        "Schoenfeld BJ, Pope ZK, Benik FM et al. (2016). Longer Interset Rest Periods Enhance Muscle Strength and Hypertrophy in Resistance-Trained Men. J Strength Cond Res 30(7):1805-1812.",
        "10.1519/JSC.0000000000001272",
    ),
    GENTIL_2015(
        "Gentil 2015",
        "Gentil P, Soares S, Bottaro M (2015). Single vs. Multi-Joint Resistance Exercises: Effects on Muscle Strength and Hypertrophy. Asian Journal of Sports Medicine 7(1):e24057.",
        "10.5812/asjsm.24057",
    ),
    WOLF_2025(
        "Wolf 2025",
        "Wolf M, Androulakis Korakakis P, Pinero A et al. (2025). Lengthened partial repetitions elicit similar muscular adaptations as full range of motion repetitions. PeerJ 13:e18904.",
        "10.7717/peerj.18904",
    ),
    WOLF_2023(
        "Wolf 2023",
        "Wolf M, Androulakis-Korakakis P, Fisher J, Schoenfeld B, Steele J (2023). Partial vs full range of motion resistance training: A systematic review and meta-analysis. Int Journal of Strength & Conditioning 3(1).",
        "10.51224/srxiv.198",
    ),
    MAEO_2021(
        "Maeo 2021",
        "Maeo S et al. (2021). Greater Hamstrings Muscle Hypertrophy but Similar Damage Protection after Training at Long versus Short Muscle Lengths. Med Sci Sports Exerc 53(3).",
        "10.1249/MSS.0000000000002523",
    ),
    MAEO_2022(
        "Maeo 2022",
        "Maeo S et al. (2022). Triceps brachii hypertrophy is substantially greater after elbow extension training performed in the overhead versus neutral arm position. European Journal of Sport Science.",
        "10.1080/17461391.2022.2100279",
    ),
    KASSIANO_2023(
        "Kassiano 2023",
        "Kassiano W et al. (2023). Greater Gastrocnemius Muscle Hypertrophy After Partial Range of Motion Training Carried Out at Long Muscle Lengths. J Strength Cond Res 37(9).",
        "10.1519/JSC.0000000000004460",
    ),
    KINOSHITA_2023(
        "Kinoshita 2023",
        "Kinoshita M et al. (2023). Triceps surae muscle hypertrophy is greater after standing versus seated calf-raise training at long muscle lengths. Frontiers in Physiology 14:1272106.",
        "10.3389/fphys.2023.1272106",
    ),
    KUBO_2019(
        "Kubo 2019",
        "Kubo K et al. (2019). Effects of squat training with different depths on lower limb muscle volumes. European Journal of Applied Physiology 119(10):2189-2196.",
        "10.1007/s00421-019-04181-y",
    ),
    PLOTKIN_2023(
        "Plotkin 2023",
        "Plotkin D et al. (2023). Hip thrust and back squat training elicit similar gluteus muscle hypertrophy and transfer similarly to the deadlift. Frontiers in Physiology 14:1279170.",
        "10.3389/fphys.2023.1279170",
    ),
    PEDROSA_2022(
        "Pedrosa 2022",
        "Pedrosa GF et al. (2022). Partial range of motion training elicits favorable improvements in muscular adaptations when carried out at long muscle lengths. European Journal of Sport Science 22(1).",
        "10.1080/17461391.2021.1927199",
    ),
    LANZA_2024(
        "Lanza 2024",
        "Lanza MB et al. (2024). Muscle hypertrophy response across four muscles involved in the bench press exercise: Randomized 10 weeks training intervention. J Bodywork & Movement Therapies.",
        "10.1016/j.jbmt.2024.07.054",
    ),
    KIKUCHI_2017(
        "Kikuchi 2017",
        "Kikuchi N, Nakazato K (2017). Low-load bench press and push-up induce similar muscle hypertrophy and strength gain. Journal of Exercise Science & Fitness 15(1):37-42.",
        "10.1016/j.jesf.2017.06.003",
    ),
    ROBERTS_2020(
        "Roberts 2020",
        "Roberts BM, Nuckols G, Krieger JW (2020). Sex Differences in Resistance Training: A Systematic Review and Meta-Analysis. J Strength Cond Res 34(5):1448-1460.",
        "10.1519/JSC.0000000000003521",
    ),
    HUNTER_2014(
        "Hunter 2014",
        "Hunter SK (2014). Sex differences in human fatigability: mechanisms and insight to physiological responses. Acta Physiologica 211(2):257-276.",
        "10.1111/apha.12234",
    ),
    LESUER_1997(
        "LeSuer 1997",
        "LeSuer DA, McCormick JH, Mayhew JL, Wasserstein RL, Arnold MD (1997). The Accuracy of Prediction Equations for Estimating 1-RM Performance in the Bench Press, Squat, and Deadlift. J Strength Cond Res 11(4):211-213.",
        "10.1519/1533-4288(1997)011<0211:TAOPEF>2.3.CO;2",
    ),
    ZOURDOS_2016(
        "Zourdos 2016",
        "Zourdos MC et al. (2016). Novel Resistance Training-Specific Rating of Perceived Exertion Scale Measuring Repetitions in Reserve. J Strength Cond Res 30(1):267-275.",
        "10.1519/JSC.0000000000001049",
    ),
    HELMS_2016(
        "Helms 2016",
        "Helms ER, Cronin J, Storey A, Zourdos MC (2016). Application of the Repetitions in Reserve-Based Rating of Perceived Exertion Scale for Resistance Training. Strength & Conditioning Journal 38(4):42-49.",
        "10.1519/SSC.0000000000000218",
    ),
    MOESGAARD_2022(
        "Moesgaard 2022",
        "Moesgaard L et al. (2022). Effects of Periodization on Strength and Muscle Hypertrophy in Volume-Equated Resistance Training Programs. Sports Medicine 52(3):615-632.",
        "10.1007/s40279-021-01636-1",
    ),
    WILLIAMS_2017(
        "Williams 2017",
        "Williams TD, Tolusso DV, Fedewa MV, Esco MR (2017). Comparison of Periodized and Non-Periodized Resistance Training on Maximal Strength. Sports Medicine 47(10):2083-2100.",
        "10.1007/s40279-017-0734-y",
    ),
    BUCKNER_2017(
        "Buckner 2017",
        "Buckner SL et al. (2017). What does individual strength say about resistance training status? Muscle & Nerve 56(4).",
        "10.1002/mus.25461",
    ),
    TASKSPEC_2025(
        "TaskSpec 2025",
        "Task Specificity of Dynamic Resistance Training and Its Transferability to Non-trained Isometric Muscle Strength: A Systematic Review with Meta-analysis. Sports Medicine (2025).",
        "10.1007/s40279-025-02225-2",
    ),
    VIGOTSKY_2022(
        "Vigotsky 2022",
        "Vigotsky AD, Halperin I, Siqueira Trajano G, Vieira TM (2022). Longing for a Longitudinal Proxy: Acutely Measured Surface EMG Amplitude is not a Validated Predictor of Muscle Hypertrophy. Sports Medicine 52(5):985-994.",
        "10.1007/s40279-021-01619-2",
    ),
    PALLARES_2019(
        "Pallares 2019",
        "Pallares JG et al. (2019). Full squat produces greater neuromuscular and functional adaptations and lower pain than partial squats after prolonged resistance training. European Journal of Sport Science.",
        "10.1080/17461391.2019.1612952",
    ),
    ;

    companion object {
        /** Short label by key, for tests and notes: `Evidence.of("Maeo 2021")`. */
        fun of(label: String): Evidence? = entries.firstOrNull { it.label == label }
    }
}
