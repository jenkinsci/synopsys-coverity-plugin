var jenkinsRootUrl = null;

function setRootURL(rootUrl) {
    jenkinsRootUrl = rootUrl;
}

function loadProjectsThenStreams(coverityInstanceUrlId, projectNameId, streamNameId, fullyQualifiedDescribable) {
    loadProjects(coverityInstanceUrlId, projectNameId, fullyQualifiedDescribable);
    loadStreams(coverityInstanceUrlId, projectNameId, streamNameId, fullyQualifiedDescribable);
}

function loadProjects(coverityInstanceUrlId, projectNameId, fullyQualifiedDescribable) {
    var coverityUrlSelect = document.getElementById(coverityInstanceUrlId);
    var coverityUrl = coverityUrlSelect.value;

    var projectSelect = document.getElementById(projectNameId);
    var oldProjectSelected = projectSelect.value;

    var fillURL = jenkinsRootUrl + "/descriptor/" + fullyQualifiedDescribable + "/fillProjectNameItems";
    var requestParameters = { coverityInstanceUrl: coverityUrl, updateNow: true };
    loadList(projectNameId, projectNameId + "Loading", 'Loading projects...', fillURL, requestParameters);
}

function loadStreams(coverityInstanceUrlId, projectNameId, streamNameId, fullyQualifiedDescribable) {
    var coverityUrlSelect = document.getElementById(coverityInstanceUrlId);
    var coverityUrl = coverityUrlSelect.value;

    var projectSelect = document.getElementById(projectNameId);
    var oldProjectSelected = projectSelect.value;

    var fillURL = jenkinsRootUrl + "/descriptor/" + fullyQualifiedDescribable + "/fillStreamNameItems";
    var requestParameters = { coverityInstanceUrl: coverityUrl, projectName: oldProjectSelected };
    loadList(streamNameId, streamNameId + "Loading", 'Loading streams...', fillURL, requestParameters);
}

function loadViews(coverityInstanceUrlId, viewNameId, fullyQualifiedDescribable) {
    var coverityUrlSelect = document.getElementById(coverityInstanceUrlId);
    var coverityUrl = coverityUrlSelect.value;

    var viewSelect = document.getElementById(viewNameId);
    if (viewSelect) {
        var fillURL = jenkinsRootUrl + "/descriptor/" + fullyQualifiedDescribable + "/fillViewNameItems";
        var requestParameters = { coverityInstanceUrl: coverityUrl, updateNow: true };
        loadList(viewNameId, viewNameId + "Loading", 'Loading views...', fillURL, requestParameters);
    }
}

function loadList(selectId, loadingId, loadingText, fillURL, requestParameters) {
    var select = document.getElementById(selectId);
    new Ajax.Request(fillURL, {
        parameters: requestParameters,
        onLoading: showLoading(selectId, loadingId, loadingText),
        onComplete: function (t) {
            if (t.status !== 200) {
                console.log("Failed to load from " + fillURL + ". Error: " + t.statusText + " status: " + t.status);
            } else if (select.options !== undefined) {
                var json = t.responseText.evalJSON();

                select.options.length = 0;

                var selectedOption = "";
                json.values.each(function (currentOption) {
                    var opt = document.createElement("option");
                    opt.value = currentOption.value;
                    opt.text = currentOption.name;
                    if (currentOption.selected) {
                        selectedOption = currentOption.value;
                    }
                    select.appendChild(opt);
                });
                select.value = selectedOption;
            }
            hideLoading(selectId, loadingId);
        }
    });
}

function showLoading(nameId, loadingId, loadingText) {
    var cell = document.getElementById(nameId).parentNode;
    if (cell) {
        var loadingDiv = cell.select("#" + loadingId).first();
        if (!loadingDiv) {
            loadingDiv = document.createElement('div');
            loadingDiv.setAttribute('id', loadingId);
            loadingDiv.innerHTML = loadingText;
            loadingDiv.hide();
            cell.appendChild(loadingDiv);
        }

        function showLoading(ld) {
            ld.show();
        }

        showLoading.delay(0.2, loadingDiv);
    }
}

function hideLoading(nameId, loadingId) {
    var cell = document.getElementById(nameId).parentNode;
    var loadingDiv = cell.select("#" + loadingId).first();
    if (cell) {
        if (loadingDiv) {
            cell.removeChild(loadingDiv);
        }
    }
}
