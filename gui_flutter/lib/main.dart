// ignore_for_file: prefer_final_fields

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:file_picker/file_picker.dart';
import 'package:path/path.dart' as p;

enum AppLanguage { en, it }

class AppStrings {
  final AppLanguage lang;
  AppStrings(this.lang);

  bool get isIt => lang == AppLanguage.it;

  String get appTitle => "OpenSplat";
  String get studioBadge => "Studio";
  String get newProject => isIt ? "Nuovo Progetto" : "New Project";
  String get closeForm => isIt ? "Chiudi Modulo" : "Close Form";
  String get systemResources => isIt ? "Risorse di Sistema" : "System Resources";
  String get projects => isIt ? "Progetti 3D" : "3D Projects";
  String get noProjects => isIt ? "Nessun progetto ancora" : "No projects yet";
  String get create3DModel => isIt ? "Crea Modello 3D" : "Create 3D Model";
  String get selectFileInstruction => isIt 
      ? "Seleziona un video (.mp4) o un dataset Nerfstudio (.zip)." 
      : "Select a video recording (.mp4) or a pre-processed Nerfstudio dataset (.zip).";
  String get selectFile => isIt ? "Seleziona File" : "Select File";
  String get clickToSelect => isIt ? "Clicca per selezionare un file dal computer" : "Click to select file from computer";
  String get fileType => isIt ? "Tipo File: " : "File Type: ";
  String get iterations => isIt ? "Iterazioni (Qualità)" : "Iterations";
  String get downscaleFactor => isIt ? "Fattore di Riduzione" : "Downscale Factor";
  String get forceCpu => isIt ? "Forza Esecuzione CPU (Fallback)" : "Force CPU Execution (Fallback)";
  String get startProcessing => isIt ? "Avvia Elaborazione 3D" : "Start 3D Processing";
  String get start => isIt ? "Avvia" : "Start";
  String get stop => isIt ? "Interrompi" : "Stop";
  String get exportModel => isIt ? "Esporta Modello" : "Export Model";
  String get exporting => isIt ? "Esportazione..." : "Exporting...";
  String get activityLogs => isIt ? "Attività e Log" : "Activity & Logs";
  String get settings => isIt ? "Impostazioni" : "Settings";
  String get studioSettings => isIt ? "Impostazioni Studio" : "Studio Settings";
  String get language => isIt ? "Lingua Interfaccia" : "Interface Language";
  String get advancedMode => isIt ? "Modalità Avanzata" : "Advanced Mode";
  String get advancedModeSubtitle => isIt 
      ? "Mostra dettagli metriche GPU/VRAM/CPU e log terminale avanzati." 
      : "Exposes detailed GPU/VRAM/CPU metrics and raw terminal log output.";
  String get liveSync => isIt ? "Sincronizzazione Server" : "Live Server Sync";
  String get liveSyncSubtitle => isIt 
      ? "Aggiorna automaticamente le metriche hardware e lo stato dei progetti." 
      : "Automatically updates hardware metrics and project status.";
  String get done => isIt ? "Fatto" : "Done";
  String get delete => isIt ? "Elimina" : "Delete";
  String get cancel => isIt ? "Annulla" : "Cancel";
  String get videoFile => isIt ? "Video (.mp4)" : "Video (.mp4)";
  String get datasetFile => isIt ? "Dataset (.zip)" : "Dataset (.zip)";
  String get noProjectSelected => isIt ? "Nessun progetto selezionato" : "No project selected";
  String get selectProjectHint => isIt 
      ? "Seleziona un progetto dal pannello sinistro o crea un nuovo modello 3D." 
      : "Select a project from the left panel or create a new 3D model.";
  String get friendlySteps => isIt ? "Passaggi" : "Steps";
  String get terminalOutput => isIt ? "Terminale" : "Terminal";
  String get clearLogs => isIt ? "Cancella Log" : "Clear Logs";
}

void main(List<String> args) {
  String apiUrl = "http://0.0.0.0:8000";
  for (int i = 0; i < args.length; i++) {
    if (args[i] == '--api-url' && i + 1 < args.length) {
      apiUrl = args[i + 1];
    } else if (args[i].startsWith('--api-url=')) {
      apiUrl = args[i].substring('--api-url='.length);
    }
  }

  final envUrl = Platform.environment['OPENSPLAT_API_URL'];
  if (envUrl != null && envUrl.isNotEmpty) {
    apiUrl = envUrl;
  }

  runApp(OpenSplatApp(apiUrl: apiUrl));
}

class OpenSplatApp extends StatelessWidget {
  final String apiUrl;
  const OpenSplatApp({super.key, required this.apiUrl});

  @override
  Widget build(BuildContext context) {
    const primaryAccent = Color(0xFF6366F1);
    const background = Color(0xFF090A0F);
    const surface = Color(0xFF11131B);

    final colorScheme = ColorScheme.dark(
      primary: primaryAccent,
      onPrimary: Colors.white,
      primaryContainer: const Color(0xFF1E1B4B),
      onPrimaryContainer: const Color(0xFFE0E7FF),
      surface: surface,
      onSurface: const Color(0xFFF1F5F9),
      onSurfaceVariant: const Color(0xFF94A3B8),
      outline: const Color(0xFF1C1E2B),
    );

    return MaterialApp(
      title: 'OpenSplat',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.dark,
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: colorScheme,
        scaffoldBackgroundColor: background,
        cardTheme: CardThemeData(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: const BorderSide(color: Color(0xFF1C1E2B), width: 1),
          ),
          color: surface,
          margin: EdgeInsets.zero,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFF161824),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFF242738)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFF242738)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: primaryAccent, width: 1.5),
          ),
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        ),
        dividerTheme: const DividerThemeData(
          color: Color(0xFF1C1E2B),
          thickness: 1,
        ),
      ),
      home: DashboardPage(apiUrl: apiUrl),
    );
  }
}

class DashboardPage extends StatefulWidget {
  final String apiUrl;
  const DashboardPage({super.key, required this.apiUrl});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  late final String _apiUrl;

  // Background Timers
  Timer? _timerJob;
  Timer? _timerSys;
  Timer? _timerList;
  // State Variables
  String _serverAddress = "";

  // Resource Stats
  double _cpuPercent = 0.0;
  double _ramPercent = 0.0;
  double _gpuPercent = 0.0;
  double _diskPercent = 0.0;

  // Upload Modal State
  bool _showNewProjectModal = false;
  int _uploadType = 0; // 0: Video (.mp4), 1: Nerfstudio Dataset (.zip)
  String? _selectedFilePath;
  int _trainingIters = 5000;
  int _downscaleFactor = 1;
  bool _forceCpu = false;

  bool _uploading = false;
  String _uploadStatusText = "";
  bool _downloading = false;

  // Jobs List
  List<dynamic> _jobs = [];
  String? _selectedJobId;

  // Selected Job Details
  String _jobDetailsStatus = "unknown";
  String _jobDetailsLoss = "Loss: N/A";
  double _jobDetailsProgress = 0.0;
  List<String> _jobLogs = [];
  bool _isJobRunning = false;

  // UI Modes & Settings
  AppLanguage _language = AppLanguage.en; // Default English

  AppStrings get _s => AppStrings(_language);

  String _formatDisplayIp(String url) {
    if (url.startsWith('http://')) return url.substring(7);
    if (url.startsWith('https://')) return url.substring(8);
    return url.isEmpty ? "Connecting..." : url;
  }

  final ScrollController _logScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _apiUrl = widget.apiUrl;
    _determineServerAddress();
    _setupTimers();
    _refreshJobList();
  }

  @override
  void dispose() {
    _timerJob?.cancel();
    _timerSys?.cancel();
    _timerList?.cancel();
    _logScrollController.dispose();
    super.dispose();
  }

  void _setupTimers() {
    _timerJob = Timer.periodic(const Duration(milliseconds: 1500), (timer) {
      _pollSelectedJobStatus();
    });
    _timerSys = Timer.periodic(const Duration(milliseconds: 2000), (timer) {
      _pollSystemStats();
    });
    _timerList = Timer.periodic(const Duration(milliseconds: 4000), (timer) {
      _refreshJobListSilent();
    });
  }

  // --- Settings Modal ---

  void _openSettingsModal() {
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setModalState) {
          final isIt = (_language == AppLanguage.it);
          return AlertDialog(
            backgroundColor: const Color(0xFF11131B),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: Color(0xFF1C1E2B)),
            ),
            title: Row(
              children: [
                const Icon(Icons.settings_rounded, size: 20, color: Color(0xFF6366F1)),
                const SizedBox(width: 10),
                Text(
                  isIt ? "Impostazioni Studio" : "Studio Settings",
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                ),
              ],
            ),
            content: SizedBox(
              width: 420,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Language Selector
                  Text(
                    isIt ? "Lingua Interfaccia" : "Interface Language",
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF94A3B8)),
                  ),
                  const SizedBox(height: 12),
                  SegmentedButton<AppLanguage>(
                    segments: const [
                      ButtonSegment<AppLanguage>(
                        value: AppLanguage.en,
                        label: Text("English (EN)"),
                        icon: Icon(Icons.language_rounded, size: 14),
                      ),
                      ButtonSegment<AppLanguage>(
                        value: AppLanguage.it,
                        label: Text("Italiano (IT)"),
                        icon: Icon(Icons.flag_rounded, size: 14),
                      ),
                    ],
                    selected: {_language},
                    onSelectionChanged: (Set<AppLanguage> sel) {
                      setState(() {
                        _language = sel.first;
                      });
                      setModalState(() {});
                    },
                    style: const ButtonStyle(visualDensity: VisualDensity.compact),
                  ),
                ],
              ),
            ),
            actions: [
              FilledButton(
                onPressed: () => Navigator.pop(ctx),
                child: Text(isIt ? "Fatto" : "Done"),
              ),
            ],
          );
        },
      ),
    );
  }

  // --- Network API Requests ---

  Future<void> _determineServerAddress() async {
    try {
      final response = await http.get(Uri.parse('$_apiUrl/api/system/stats')).timeout(const Duration(seconds: 2));
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final ip = data['local_ip'] ?? "0.0.0.0";
        final port = Uri.parse(_apiUrl).port;
        setState(() {
          _serverAddress = "http://$ip:$port";
        });
      }
    } catch (_) {
      setState(() {
        _serverAddress = _apiUrl;
      });
    }
  }

  Future<void> _pollSystemStats() async {
    try {
      final response = await http.get(Uri.parse('$_apiUrl/api/system/stats')).timeout(const Duration(seconds: 2));
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (mounted) {
          setState(() {
            _cpuPercent = (data['cpu']?['percent'] ?? 0.0).toDouble();
            _ramPercent = (data['ram']?['percent'] ?? 0.0).toDouble();
            _diskPercent = (data['disk']?['percent'] ?? 0.0).toDouble();

            final gpu = data['gpu'];
            if (gpu != null && gpu['available'] == true) {
              _gpuPercent = (gpu['load'] ?? 0.0).toDouble();
            } else {
              _gpuPercent = 0.0;
            }
          });
        }
      }
    } catch (_) {}
  }

  Future<void> _refreshJobList() async {
    try {
      final response = await http.get(Uri.parse('$_apiUrl/api/jobs')).timeout(const Duration(seconds: 3));
      if (response.statusCode == 200) {
        final List<dynamic> data = jsonDecode(response.body);
        if (mounted) {
          setState(() {
            _jobs = data;
            if (_selectedJobId == null && _jobs.isNotEmpty) {
              _selectedJobId = _jobs.first['job_id'];
              _pollSelectedJobStatus();
            }
          });
        }
      }
    } catch (_) {}
  }

  Future<void> _refreshJobListSilent() async {
    try {
      final response = await http.get(Uri.parse('$_apiUrl/api/jobs')).timeout(const Duration(seconds: 3));
      if (response.statusCode == 200) {
        final List<dynamic> data = jsonDecode(response.body);
        if (mounted) {
          setState(() {
            _jobs = data;
          });
        }
      }
    } catch (_) {}
  }

  Future<void> _pollSelectedJobStatus() async {
    if (_selectedJobId == null) return;
    try {
      final response = await http.get(Uri.parse('$_apiUrl/api/jobs/$_selectedJobId/status')).timeout(const Duration(seconds: 2));
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final meta = data['metadata'] ?? {};
        final List<dynamic> rawLogs = data['logs'] ?? [];

        if (mounted) {
          setState(() {
            final status = (meta['status'] ?? "unknown").toString().toLowerCase();
            _isJobRunning = (status == "preprocessing" || status == "training");
            _jobDetailsStatus = status;

            final loss = (meta['loss'] ?? 0.0) as num;
            final step = meta['step'] ?? 0;
            final totalSteps = meta['total_steps'] ?? 30000;
            _jobDetailsLoss = "Loss: ${loss.toStringAsFixed(5)} • Step $step / $totalSteps";
            _jobDetailsProgress = (meta['progress'] ?? 0.0).toDouble();

            _jobLogs = rawLogs.map((l) => l.toString()).toList();
          });

          _scrollToBottom();
        }
      }
    } catch (_) {}
  }

  void _scrollToBottom() {
    if (_logScrollController.hasClients) {
      _logScrollController.animateTo(
        _logScrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 100),
        curve: Curves.easeOut,
      );
    }
  }

  Future<void> _startJob(String jobId) async {
    try {
      final response = await http.post(
        Uri.parse('$_apiUrl/api/jobs/$jobId/start'),
        body: {
          'num_iters': _trainingIters.toString(),
          'downscale': _downscaleFactor.toString(),
          'use_cpu': _forceCpu.toString(),
        },
      ).timeout(const Duration(seconds: 3));

      if (response.statusCode == 200) {
        _pollSelectedJobStatus();
        _refreshJobListSilent();
      }
    } catch (e) {
      _showErrorSnackBar("Failed to start job: $e");
    }
  }

  Future<void> _stopJob(String jobId) async {
    try {
      final response = await http.post(Uri.parse('$_apiUrl/api/jobs/$jobId/stop')).timeout(const Duration(seconds: 3));
      if (response.statusCode == 200) {
        _pollSelectedJobStatus();
        _refreshJobListSilent();
      }
    } catch (e) {
      _showErrorSnackBar("Failed to stop job: $e");
    }
  }

  Future<void> _deleteJob(String jobId) async {
    final confirm = await _showConfirmDialog(
      _s.delete,
      _s.isIt ? "Sei sicuro di voler eliminare '$jobId'?" : "Are you sure you want to delete '$jobId'?"
    );
    if (!confirm) return;

    try {
      final response = await http.delete(Uri.parse('$_apiUrl/api/jobs/$jobId')).timeout(const Duration(seconds: 3));
      if (response.statusCode == 200) {
        setState(() {
          if (_selectedJobId == jobId) {
            _selectedJobId = null;
            _jobDetailsStatus = "unknown";
            _jobDetailsLoss = "Loss: N/A";
            _jobDetailsProgress = 0.0;
            _jobLogs = [];
          }
        });
        _refreshJobList();
      }
    } catch (e) {
      _showErrorSnackBar("Error deleting job: $e");
    }
  }

  Future<void> _clearLogs(String jobId) async {
    try {
      final response = await http.delete(Uri.parse('$_apiUrl/api/jobs/$jobId/logs')).timeout(const Duration(seconds: 3));
      if (response.statusCode == 200) {
        setState(() {
          _jobLogs = [];
        });
      }
    } catch (e) {
      _showErrorSnackBar("Error clearing logs: $e");
    }
  }

  Future<void> _browseFile() async {
    final fileFilter = _uploadType == 0 ? ['mp4'] : ['zip'];
    final result = await FilePicker.pickFiles(
      type: FileType.custom,
      allowedExtensions: fileFilter,
    );

    if (result != null && result.files.single.path != null) {
      setState(() {
        _selectedFilePath = result.files.single.path;
      });
    }
  }

  Future<void> _submitUpload() async {
    if (_selectedFilePath == null || _selectedFilePath!.isEmpty) {
      _showErrorSnackBar(_s.isIt ? "Seleziona un file da caricare." : "Please select a file to upload.");
      return;
    }

    final file = File(_selectedFilePath!);
    if (!await file.exists()) {
      _showErrorSnackBar(_s.isIt ? "Il file selezionato non esiste." : "Selected file does not exist.");
      return;
    }

    setState(() {
      _uploading = true;
      _uploadStatusText = _s.isIt ? "Caricamento file..." : "Uploading file...";
    });

    try {
      final endpoint = _uploadType == 0 ? "/api/upload/video" : "/api/upload/dataset";
      final request = http.MultipartRequest('POST', Uri.parse('$_apiUrl$endpoint'));
      request.files.add(await http.MultipartFile.fromPath('file', _selectedFilePath!));

      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);

      if (response.statusCode == 200) {
        final body = jsonDecode(response.body);
        final jobId = body['job_id'];

        await _startJobSilent(jobId);

        setState(() {
          _uploading = false;
          _selectedFilePath = null;
          _uploadStatusText = "";
          _showNewProjectModal = false;
          _selectedJobId = jobId;
        });

        _refreshJobList();
        _pollSelectedJobStatus();
      } else {
        final errBody = jsonDecode(response.body);
        setState(() {
          _uploading = false;
        });
        _showErrorSnackBar("Upload failed: ${errBody['detail'] ?? 'Error'}");
      }
    } catch (e) {
      setState(() {
        _uploading = false;
      });
      _showErrorSnackBar("Upload error: $e");
    }
  }

  Future<bool> _startJobSilent(String jobId) async {
    try {
      final response = await http.post(
        Uri.parse('$_apiUrl/api/jobs/$jobId/start'),
        body: {
          'num_iters': _trainingIters.toString(),
          'downscale': _downscaleFactor.toString(),
          'use_cpu': _forceCpu.toString(),
        },
      ).timeout(const Duration(seconds: 3));
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<void> _downloadModel() async {
    if (_selectedJobId == null) return;

    final savePath = await FilePicker.saveFile(
      dialogTitle: _s.isIt ? "Esporta Modello 3D (.ply)" : "Export 3D Model (.ply)",
      fileName: "$_selectedJobId.ply",
      type: FileType.custom,
      allowedExtensions: ['ply'],
    );

    if (savePath == null) return;

    setState(() {
      _downloading = true;
    });

    try {
      final request = http.Request('GET', Uri.parse('$_apiUrl/api/jobs/$_selectedJobId/download'));
      final streamedResponse = await http.Client().send(request);

      if (streamedResponse.statusCode == 200) {
        final file = File(savePath);
        final sink = file.openWrite();

        await streamedResponse.stream.forEach((chunk) {
          sink.add(chunk);
        });

        await sink.close();

        setState(() {
          _downloading = false;
        });

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(_s.isIt ? "Modello esportato con successo in $savePath" : "Model exported successfully to $savePath")),
          );
        }
      } else {
        setState(() {
          _downloading = false;
        });
        _showErrorSnackBar("Export failed (Code ${streamedResponse.statusCode})");
      }
    } catch (e) {
      setState(() {
        _downloading = false;
      });
      _showErrorSnackBar("Export failed: $e");
    }
  }

  void _showErrorSnackBar(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.redAccent,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  Future<bool> _showConfirmDialog(String title, String message) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        content: Text(message, style: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8))),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(_s.cancel)),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(_s.delete),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  // --- Main Build Layout ---

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      body: Column(
        children: [
          // 1. Ultra-Clean Minimal Top Bar
          Container(
            height: 52,
            decoration: const BoxDecoration(
              color: Color(0xFF0D0E15),
              border: Border(bottom: BorderSide(color: Color(0xFF1C1E2B), width: 1)),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              children: [
                // Minimal Logo Mark
                Container(
                  width: 28,
                  height: 28,
                  decoration: BoxDecoration(
                    color: colorScheme.primary,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: const Center(
                    child: Icon(Icons.auto_awesome_rounded, color: Colors.white, size: 16),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  _s.appTitle,
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, letterSpacing: -0.3, color: Colors.white),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1C1E2B),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(_s.studioBadge, style: const TextStyle(fontSize: 10, color: Color(0xFF94A3B8), fontWeight: FontWeight.w500)),
                ),
                const Spacer(),

                // Server IP Pill (Static Badge)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1E2032),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: const Color(0xFF383C5A), width: 1.2),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          color: Color(0xFF10B981),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        _formatDisplayIp(_serverAddress),
                        style: const TextStyle(
                          fontSize: 13,
                          fontFamily: 'monospace',
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF38BDF8),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),

                // Settings Gear Icon Button (Opens Settings Modal Popup)
                IconButton(
                  onPressed: _openSettingsModal,
                  icon: const Icon(Icons.settings_rounded, size: 20, color: Color(0xFF94A3B8)),
                  tooltip: _s.settings,
                ),
              ],
            ),
          ),

          // 2. Main Workspace Split (Sidebar Left, Active Project Center)
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // SIDEBAR LEFT (Projects List + System Stats Footer)
                Container(
                  width: 280,
                  decoration: const BoxDecoration(
                    color: Color(0xFF0B0C12),
                    border: Border(right: BorderSide(color: Color(0xFF1C1E2B), width: 1)),
                  ),
                  child: Column(
                    children: [
                      // "+ New Project" Button
                      Padding(
                        padding: const EdgeInsets.all(12.0),
                        child: SizedBox(
                          width: double.infinity,
                          child: FilledButton.icon(
                            onPressed: () {
                              setState(() {
                                _showNewProjectModal = !_showNewProjectModal;
                              });
                            },
                            icon: Icon(_showNewProjectModal ? Icons.close_rounded : Icons.add_rounded, size: 18),
                            label: Text(_showNewProjectModal ? _s.closeForm : _s.newProject),
                            style: FilledButton.styleFrom(
                              backgroundColor: _showNewProjectModal ? const Color(0xFF242738) : colorScheme.primary,
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                              padding: const EdgeInsets.symmetric(vertical: 10),
                            ),
                          ),
                        ),
                      ),

                      // Projects List
                      Expanded(
                        child: _jobs.isEmpty
                            ? Center(
                                child: Text(_s.noProjects, style: const TextStyle(fontSize: 12, color: Color(0xFF64748B))),
                              )
                            : ListView.builder(
                                padding: const EdgeInsets.symmetric(horizontal: 8),
                                itemCount: _jobs.length,
                                itemBuilder: (ctx, idx) {
                                  final job = _jobs[idx];
                                  final jobId = job['job_id'] ?? "";
                                  final status = (job['status'] ?? "unknown").toString().toLowerCase();
                                  final isSelected = (_selectedJobId == jobId);

                                  Color statusDotColor = Colors.grey;
                                  if (status == "completed") {
                                    statusDotColor = Colors.greenAccent;
                                  } else if (status == "preprocessing" || status == "training") {
                                    statusDotColor = Colors.lightBlueAccent;
                                  } else if (status == "failed" || status == "stopped") {
                                    statusDotColor = Colors.redAccent;
                                  }

                                  return InkWell(
                                    onTap: () {
                                      setState(() {
                                        _selectedJobId = jobId;
                                        _showNewProjectModal = false;
                                      });
                                      _pollSelectedJobStatus();
                                    },
                                    borderRadius: BorderRadius.circular(8),
                                    child: Container(
                                      margin: const EdgeInsets.only(bottom: 4),
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                                      decoration: BoxDecoration(
                                        color: isSelected ? const Color(0xFF1A1D2B) : Colors.transparent,
                                        borderRadius: BorderRadius.circular(8),
                                        border: isSelected ? Border.all(color: const Color(0xFF2A2E44)) : null,
                                      ),
                                      child: Row(
                                        children: [
                                          Container(
                                            width: 7,
                                            height: 7,
                                            decoration: BoxDecoration(
                                              shape: BoxShape.circle,
                                              color: statusDotColor,
                                            ),
                                          ),
                                          const SizedBox(width: 10),
                                          Expanded(
                                            child: Text(
                                              jobId,
                                              style: TextStyle(
                                                fontSize: 12,
                                                fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                                                color: isSelected ? Colors.white : const Color(0xFF94A3B8),
                                              ),
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                          ),
                                          if (isSelected)
                                            IconButton(
                                              icon: const Icon(Icons.more_vert_rounded, size: 14, color: Color(0xFF64748B)),
                                              onPressed: () => _deleteJob(jobId),
                                              padding: EdgeInsets.zero,
                                              constraints: const BoxConstraints(),
                                            ),
                                        ],
                                      ),
                                    ),
                                  );
                                },
                              ),
                      ),

                      // System Footer (Compact Pills vs Advanced Details)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
                        decoration: const BoxDecoration(
                          border: Border(top: BorderSide(color: Color(0xFF1C1E2B), width: 1)),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            _buildMiniMetric("CPU", _cpuPercent, Icons.memory_rounded),
                            _buildMiniMetric("RAM", _ramPercent, Icons.storage_rounded),
                            _buildMiniMetric("GPU", _gpuPercent, Icons.developer_board_rounded),
                            _buildMiniMetric("Disk", _diskPercent, Icons.disc_full_rounded),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),

                // RIGHT MAIN WORKSPACE
                Expanded(
                  child: _showNewProjectModal
                      ? _buildNewProjectFormView()
                      : _buildActiveProjectWorkspace(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMiniMetric(String label, double val, IconData icon) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 4),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(label, style: const TextStyle(fontSize: 9, color: Color(0xFF64748B), fontWeight: FontWeight.w600)),
            Text("${val.toStringAsFixed(0)}%", style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFFCBD5E1))),
          ],
        ),
      ],
    );
  }

  // --- Workspace Views ---

  Widget _buildNewProjectFormView() {
    final colorScheme = Theme.of(context).colorScheme;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(32),
      child: Center(
        child: Container(
          constraints: const BoxConstraints(maxWidth: 600),
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: const Color(0xFF11131B),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFF1C1E2B)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Text(_s.create3DModel, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white)),
                  const Spacer(),
                  IconButton(
                    icon: const Icon(Icons.close_rounded, size: 20),
                    onPressed: () => setState(() => _showNewProjectModal = false),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(_s.selectFileInstruction, style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8))),
              const SizedBox(height: 20),

              // File Selection Box
              InkWell(
                onTap: _uploading ? null : _browseFile,
                borderRadius: BorderRadius.circular(10),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
                  decoration: BoxDecoration(
                    color: const Color(0xFF161824),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: _selectedFilePath != null ? colorScheme.primary : const Color(0xFF2B2F44),
                      width: _selectedFilePath != null ? 1.5 : 1,
                    ),
                  ),
                  child: Column(
                    children: [
                      Icon(
                        _selectedFilePath != null ? Icons.check_circle_outline_rounded : Icons.cloud_upload_outlined,
                        size: 32,
                        color: _selectedFilePath != null ? colorScheme.primary : const Color(0xFF64748B),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        _selectedFilePath != null ? p.basename(_selectedFilePath!) : _s.clickToSelect,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: _selectedFilePath != null ? Colors.white : const Color(0xFF94A3B8),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),

              // Format Selector Segmented Control
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_s.fileType, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF94A3B8))),
                  const SizedBox(height: 6),
                  SizedBox(
                    width: double.infinity,
                    child: SegmentedButton<int>(
                      segments: [
                        ButtonSegment<int>(value: 0, label: Text(_s.videoFile)),
                        ButtonSegment<int>(value: 1, label: Text(_s.datasetFile)),
                      ],
                      selected: {_uploadType},
                      onSelectionChanged: (Set<int> sel) {
                        setState(() {
                          _uploadType = sel.first;
                          _selectedFilePath = null;
                        });
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Iterations Input Field
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(_s.iterations, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF94A3B8))),
                  const SizedBox(height: 6),
                  TextFormField(
                    initialValue: _trainingIters.toString(),
                    keyboardType: TextInputType.number,
                    style: const TextStyle(fontSize: 13),
                    decoration: const InputDecoration(
                      isDense: true,
                      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    ),
                    onChanged: (val) {
                      final pVal = int.tryParse(val);
                      if (pVal != null && pVal >= 100) {
                        _trainingIters = pVal;
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Upload status
              if (_uploading) ...[
                Text(_uploadStatusText, style: const TextStyle(fontSize: 12, color: Colors.amber)),
                const SizedBox(height: 6),
                const LinearProgressIndicator(),
                const SizedBox(height: 16),
              ],

              // Submit Button
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _uploading ? null : _submitUpload,
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  child: Text(_s.startProcessing, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActiveProjectWorkspace() {
    final colorScheme = Theme.of(context).colorScheme;

    if (_selectedJobId == null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.view_in_ar_rounded, size: 48, color: Color(0xFF2B2F44)),
            const SizedBox(height: 16),
            Text(_s.noProjectSelected, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white)),
            const SizedBox(height: 6),
            Text(_s.selectProjectHint, style: const TextStyle(fontSize: 12, color: Color(0xFF64748B))),
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: () => setState(() => _showNewProjectModal = true),
              icon: const Icon(Icons.add_rounded, size: 18),
              label: Text(_s.newProject),
            ),
          ],
        ),
      );
    }

    final status = _jobDetailsStatus.toLowerCase();
    Color statusColor = Colors.grey;
    if (status == "completed") statusColor = Colors.greenAccent;
    if (status == "training" || status == "preprocessing") statusColor = Colors.lightBlueAccent;
    if (status == "failed" || status == "stopped") statusColor = Colors.redAccent;

    return Padding(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Project Title & Status Bar
          Row(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _selectedJobId!,
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white, letterSpacing: -0.5),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: BoxDecoration(shape: BoxShape.circle, color: statusColor),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        status.toUpperCase(),
                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: statusColor),
                      ),
                      const SizedBox(width: 12),
                      Text(_jobDetailsLoss, style: const TextStyle(fontSize: 11, color: Color(0xFF64748B), fontFamily: 'monospace')),
                    ],
                  ),
                ],
              ),
              const Spacer(),

              // Quick Actions Bar
              Wrap(
                spacing: 8,
                children: [
                  if (!_isJobRunning)
                    OutlinedButton.icon(
                      onPressed: () => _startJob(_selectedJobId!),
                      icon: const Icon(Icons.play_arrow_rounded, size: 16),
                      label: Text(_s.start),
                    ),
                  if (_isJobRunning)
                    OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(foregroundColor: Colors.redAccent),
                      onPressed: () => _stopJob(_selectedJobId!),
                      icon: const Icon(Icons.stop_rounded, size: 16),
                      label: Text(_s.stop),
                    ),
                  FilledButton.icon(
                    onPressed: (status == "completed" && !_downloading) ? _downloadModel : null,
                    icon: _downloading
                        ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.download_rounded, size: 16),
                    label: Text(_downloading ? _s.exporting : _s.exportModel),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),

          // Thin Progress Bar
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: (_jobDetailsProgress / 100.0).clamp(0.0, 1.0),
              minHeight: 6,
              backgroundColor: const Color(0xFF161824),
              valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
            ),
          ),
          const SizedBox(height: 20),

          // Activity Header
          Row(
            children: [
              Text(_s.activityLogs, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Color(0xFFCBD5E1))),
              const Spacer(),
              IconButton(
                icon: const Icon(Icons.delete_sweep_rounded, size: 18, color: Color(0xFF64748B)),
                onPressed: () => _clearLogs(_selectedJobId!),
                tooltip: _s.clearLogs,
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Activity Box Content
          Expanded(
            child: _buildMinimalStepsView(),
          ),
        ],
      ),
    );
  }

  Widget _buildMinimalStepsView() {
    if (_jobLogs.isEmpty) {
      return Container(
        decoration: BoxDecoration(
          color: const Color(0xFF11131B),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFF1C1E2B)),
        ),
        child: Center(
          child: Text(
            _s.isIt ? "Nessuna attività registrata." : "No activity recorded yet.",
            style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
          ),
        ),
      );
    }

    final parsed = _parseLogsMinimal(_jobLogs);

    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF11131B),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFF1C1E2B)),
      ),
      padding: const EdgeInsets.all(12),
      child: ListView.builder(
        controller: _logScrollController,
        itemCount: parsed.length,
        itemBuilder: (ctx, idx) {
          final item = parsed[idx];
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 8),
            child: Row(
              children: [
                Icon(item.icon, size: 16, color: item.iconColor),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    item.text,
                    style: TextStyle(fontSize: 12, color: item.textColor, fontWeight: item.isHighlight ? FontWeight.bold : FontWeight.normal),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }



  List<_MinimalLogItem> _parseLogsMinimal(List<String> logs) {
    final List<_MinimalLogItem> items = [];
    final isIt = (_language == AppLanguage.it);

    for (final raw in logs) {
      final line = raw.trim();
      if (line.isEmpty) continue;

      if (line.contains("Starting") || line.contains("job started")) {
        items.add(_MinimalLogItem(
          text: isIt ? "Inizializzazione progetto avviata" : "Project initialization started",
          icon: Icons.rocket_launch_rounded,
          iconColor: Colors.indigoAccent,
          textColor: Colors.white,
          isHighlight: true,
        ));
      } else if (line.contains("Extracting video frames") || line.contains("COLMAP") || line.contains("preprocessing")) {
        items.add(_MinimalLogItem(
          text: isIt ? "Pre-elaborazione fotogrammi e coordinate fotocamera" : "Preprocessing frames & camera coordinates",
          icon: Icons.camera_rounded,
          iconColor: Colors.lightBlueAccent,
          textColor: const Color(0xFFCBD5E1),
          isHighlight: false,
        ));
      } else if (line.contains("Iteration") || line.contains("Step")) {
        items.add(_MinimalLogItem(
          text: line,
          icon: Icons.auto_awesome_rounded,
          iconColor: Colors.amber,
          textColor: const Color(0xFFCBD5E1),
          isHighlight: false,
        ));
      } else if (line.contains("Completed") || line.contains("FINISHED")) {
        items.add(_MinimalLogItem(
          text: isIt ? "Generazione modello 3D Gaussian Splat completata" : "3D Gaussian Splat model generation complete",
          icon: Icons.check_circle_rounded,
          iconColor: Colors.greenAccent,
          textColor: Colors.white,
          isHighlight: true,
        ));
      } else if (line.contains("Error") || line.contains("FAILED")) {
        items.add(_MinimalLogItem(
          text: line,
          icon: Icons.error_rounded,
          iconColor: Colors.redAccent,
          textColor: Colors.redAccent,
          isHighlight: true,
        ));
      } else {
        items.add(_MinimalLogItem(
          text: line,
          icon: Icons.info_outline_rounded,
          iconColor: const Color(0xFF64748B),
          textColor: const Color(0xFF94A3B8),
          isHighlight: false,
        ));
      }
    }

    return items;
  }
}

class _MinimalLogItem {
  final String text;
  final IconData icon;
  final Color iconColor;
  final Color textColor;
  final bool isHighlight;

  _MinimalLogItem({
    required this.text,
    required this.icon,
    required this.iconColor,
    required this.textColor,
    required this.isHighlight,
  });
}
