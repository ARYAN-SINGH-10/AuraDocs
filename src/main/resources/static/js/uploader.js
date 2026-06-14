// PDF Toolkit — Uploader & Processing Controller
document.addEventListener('DOMContentLoaded', () => {

    // ── DOM references ──────────────────────────────────────────────────────
    const dropzone        = document.getElementById('dropzone');
    const fileInput       = document.getElementById('file-input');
    const filesContainer  = document.getElementById('files-container');
    const filesList       = document.getElementById('files-list');
    const clearBtn        = document.getElementById('clear-btn');
    const processBtn      = document.getElementById('process-btn');
    const loadingOverlay  = document.getElementById('loading-overlay');
    const loadingText     = document.getElementById('loading-text');
    const progressBar     = document.getElementById('progress-bar');
    const successOverlay  = document.getElementById('success-overlay');
    const downloadBtn     = document.getElementById('download-btn');
    const resetBtn        = document.getElementById('reset-btn');
    const toastContainer  = document.getElementById('toast-container');
    const addMoreBtn      = document.getElementById('add-more-btn');

    // Split-specific elements (only present on split page)
    const splitMode   = document.getElementById('split-mode');
    const rangeInputs = document.getElementById('range-inputs');

    // Rotate-specific (only present on rotate page)
    const rotateSelect = document.getElementById('rotate-degrees');

    // Protect-specific
    const passwordInput = document.getElementById('protect-password');

    // Watermark-specific
    const watermarkInput = document.getElementById('watermark-text');

    // Delete-specific
    const pagesToDelete = document.getElementById('pages-to-delete');

    let selectedFiles = [];

    // Extract-specific
    const pagesToExtract = document.getElementById('pages-to-extract');

    // ── Open file picker — single click only ───────────────────────────────
    // The "Select Files" button uses data-trigger="file-input" to open the picker.
    // The dropzone itself ALSO opens it when you click the empty area.
    function openPicker() {
        fileInput.value = ''; // Reset so selecting same file again fires 'change'
        fileInput.click();
    }

    // Clicks on the dropzone (not on children with their own handlers) open picker
    dropzone.addEventListener('click', (e) => {
        if (e.target.closest('[data-trigger="file-input"]') || e.target === dropzone ||
            e.target.closest('#dropzone')) {
            openPicker();
        }
    });

    if (addMoreBtn) {
        addMoreBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            openPicker();
        });
    }

    // ── Drag & Drop ─────────────────────────────────────────────────────────
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(ev =>
        dropzone.addEventListener(ev, e => { e.preventDefault(); e.stopPropagation(); }));

    ['dragenter', 'dragover'].forEach(ev =>
        dropzone.addEventListener(ev, () =>
            dropzone.classList.add('border-indigo-500', '!bg-indigo-50/30', 'dark:!bg-indigo-950/10')));

    ['dragleave', 'drop'].forEach(ev =>
        dropzone.addEventListener(ev, () =>
            dropzone.classList.remove('border-indigo-500', '!bg-indigo-50/30', 'dark:!bg-indigo-950/10')));

    dropzone.addEventListener('drop', e => handleFiles(e.dataTransfer.files));
    fileInput.addEventListener('change', () => { if (fileInput.files.length) handleFiles(fileInput.files); });

    // ── File handling ───────────────────────────────────────────────────────
    function getAcceptedExtensions() {
        const raw = fileInput.getAttribute('accept') || '.pdf';
        return raw.split(',').map(s => s.trim().toLowerCase());
    }

    function isFileAccepted(file) {
        const accepted = getAcceptedExtensions();
        // Accept everything if '*' present
        if (accepted.includes('*')) return true;
        const name = file.name.toLowerCase();
        return accepted.some(ext => name.endsWith(ext));
    }

    function handleFiles(files) {
        if (!TOOL_CONFIG.multiple) {
            selectedFiles = []; // Single-file mode: replace existing
        }

        for (let i = 0; i < files.length; i++) {
            const file = files[i];
            if (!isFileAccepted(file)) {
                const accepted = getAcceptedExtensions().join(', ');
                showToast(`"${file.name}" is not a supported file type (${accepted}).`, 'error');
                continue;
            }
            selectedFiles.push(file);
        }
        updateFilesListUI();
    }

    // ── Files list UI ───────────────────────────────────────────────────────
    function updateFilesListUI() {
        filesList.innerHTML = '';

        if (selectedFiles.length === 0) {
            dropzone.classList.remove('hidden');
            filesContainer.classList.add('hidden');
            return;
        }

        dropzone.classList.add('hidden');
        filesContainer.classList.remove('hidden');

        selectedFiles.forEach((file, index) => {
            const li = document.createElement('li');
            li.className = 'flex items-center justify-between p-3 bg-slate-50 dark:bg-slate-900 ' +
                           'border border-slate-100 dark:border-slate-800 rounded-xl';

            if (TOOL_CONFIG.multiple) {
                li.setAttribute('draggable', 'true');
                li.classList.add('cursor-grab', 'active:cursor-grabbing');
                li.addEventListener('dragstart', e => {
                    e.dataTransfer.setData('text/plain', index);
                    li.classList.add('opacity-50');
                });
                li.addEventListener('dragend', () => li.classList.remove('opacity-50'));
                li.addEventListener('dragover', e => e.preventDefault());
                li.addEventListener('drop', e => {
                    const from = parseInt(e.dataTransfer.getData('text/plain'), 10);
                    if (from !== index) {
                        selectedFiles.splice(index, 0, selectedFiles.splice(from, 1)[0]);
                        updateFilesListUI();
                    }
                });
            }

            const sizeMb = (file.size / (1024 * 1024)).toFixed(2);
            const icon   = getFileIcon(file.name);
            li.innerHTML = `
                <div class="flex items-center gap-3 overflow-hidden">
                    <div class="p-2 bg-indigo-50 dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 rounded-lg flex-shrink-0">
                        <i data-lucide="${icon}" class="w-5 h-5"></i>
                    </div>
                    <div class="overflow-hidden">
                        <span class="text-sm font-semibold text-slate-900 dark:text-white block truncate max-w-xs">${escHtml(file.name)}</span>
                        <span class="text-xs text-slate-400 dark:text-slate-500 block">${sizeMb} MB</span>
                    </div>
                </div>
                <button type="button" data-remove="${index}"
                    class="p-1.5 text-slate-400 hover:text-rose-500 rounded-lg hover:bg-slate-100
                           dark:hover:bg-slate-800 transition-colors duration-200 flex-shrink-0">
                    <i data-lucide="trash-2" class="w-4 h-4"></i>
                </button>`;

            li.querySelector('[data-remove]').addEventListener('click', e => {
                e.stopPropagation();
                selectedFiles.splice(index, 1);
                updateFilesListUI();
            });

            filesList.appendChild(li);
        });

        lucide.createIcons();
    }

    function getFileIcon(name) {
        const lc = name.toLowerCase();
        if (lc.endsWith('.pdf'))                         return 'file-text';
        if (lc.endsWith('.doc') || lc.endsWith('.docx')) return 'file-type';
        if (lc.endsWith('.xls') || lc.endsWith('.xlsx')) return 'file-spreadsheet';
        if (lc.endsWith('.ppt') || lc.endsWith('.pptx')) return 'presentation';
        if (lc.endsWith('.html') || lc.endsWith('.htm')) return 'code';
        if (['.jpg','.jpeg','.png','.gif','.webp'].some(e => lc.endsWith(e))) return 'image';
        return 'file';
    }

    function escHtml(str) {
        return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
                  .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
    }

    // ── Buttons ─────────────────────────────────────────────────────────────
    clearBtn.addEventListener('click', () => {
        selectedFiles = [];
        updateFilesListUI();
    });

    resetBtn.addEventListener('click', () => {
        selectedFiles = [];
        updateFilesListUI();
        successOverlay.classList.add('hidden');
    });

    // ── Process / Upload ────────────────────────────────────────────────────
    processBtn.addEventListener('click', () => {
        if (selectedFiles.length === 0) {
            showToast('Please select at least one file first.', 'error');
            return;
        }

        // ── Required-field validation ───────────────────────────────────────
        if (watermarkInput !== null && watermarkInput.value.trim() === '') {
            showToast('Please enter the watermark text before processing.', 'error');
            watermarkInput.focus();
            return;
        }
        if (passwordInput !== null && passwordInput.value.trim() === '') {
            if (TOOL_CONFIG.toolName === 'Protect Document') {
                showToast('Please set a password to protect this document.', 'error');
                passwordInput.focus();
                return;
            }
        }

        const formData = new FormData();
        selectedFiles.forEach(file => formData.append('files', file));

        // Append extra options based on what controls exist
        if (splitMode) {
            formData.append('splitMode', splitMode.value);
            const param = document.getElementById('split-param');
            if (param && param.value) {
                formData.append('splitParam', param.value);
            }
        }
        if (pagesToExtract && pagesToExtract.value.trim()) {
            formData.append('pagesToExtract', pagesToExtract.value.trim());
        }
        if (rotateSelect)   formData.append('degrees', rotateSelect.value);
        if (passwordInput && passwordInput.value.trim())
            formData.append('password', passwordInput.value.trim());
        if (watermarkInput && watermarkInput.value.trim())
            formData.append('text', watermarkInput.value.trim());
        if (pagesToDelete && pagesToDelete.value.trim())
            formData.append('pagesToDelete', pagesToDelete.value.trim());

        // Show loading
        loadingOverlay.classList.remove('hidden');
        progressBar.style.width = '0%';
        loadingText.textContent = 'Uploading files…';

        const xhr = new XMLHttpRequest();
        xhr.open('POST', TOOL_CONFIG.actionUrl, true);

        xhr.upload.addEventListener('progress', e => {
            if (e.lengthComputable) {
                const pct = Math.round((e.loaded / e.total) * 100);
                progressBar.style.width = pct + '%';
                if (pct === 100) loadingText.textContent = 'Processing on server…';
            }
        });

        xhr.onload = () => {
            loadingOverlay.classList.add('hidden');
            if (xhr.status === 200) {
                try {
                    const resp = JSON.parse(xhr.responseText);
                    if (resp.success && resp.downloadUrl) {
                        downloadBtn.href = resp.downloadUrl;
                        successOverlay.classList.remove('hidden');
                        showToast('File processed successfully!', 'success');
                    } else {
                        showToast(resp.message || 'Processing failed.', 'error');
                    }
                } catch (_) {
                    showToast('Server returned an invalid response.', 'error');
                }
            } else {
                let errMsg = 'Server error (' + xhr.status + ')';
                try {
                    const resp = JSON.parse(xhr.responseText);
                    if (resp.message) errMsg = resp.message;
                } catch (_) {}
                showToast(errMsg, 'error');
            }
        };

        xhr.onerror = () => {
            loadingOverlay.classList.add('hidden');
            showToast('Network error. Please check your connection.', 'error');
        };

        xhr.send(formData);
    });

    // ── Toast ───────────────────────────────────────────────────────────────
    function showToast(message, type = 'info') {
        const colors = {
            error:   'bg-rose-50 border-rose-200 text-rose-800 dark:bg-rose-950/40 dark:border-rose-900/50 dark:text-rose-400',
            success: 'bg-emerald-50 border-emerald-200 text-emerald-800 dark:bg-emerald-950/40 dark:border-emerald-900/50 dark:text-emerald-400',
            info:    'bg-indigo-50 border-indigo-200 text-indigo-800 dark:bg-indigo-950/40 dark:border-indigo-900/50 dark:text-indigo-400'
        };
        const icons  = { error: 'alert-circle', success: 'check-circle', info: 'info' };

        const toast  = document.createElement('div');
        toast.className = `flex items-center gap-3 px-4 py-3 rounded-xl border text-sm font-medium
                           shadow-md transition-all duration-300 opacity-0 translate-y-2 ${colors[type] || colors.info}`;
        toast.innerHTML = `<i data-lucide="${icons[type] || 'info'}" class="w-5 h-5 flex-shrink-0"></i>
                           <span>${escHtml(message)}</span>`;
        toastContainer.appendChild(toast);
        lucide.createIcons();

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                toast.classList.remove('opacity-0', 'translate-y-2');
            });
        });

        setTimeout(() => {
            toast.classList.add('opacity-0', 'translate-y-2');
            setTimeout(() => toast.remove(), 300);
        }, 4500);
    }
});
