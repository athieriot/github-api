/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.kohsuke.github;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.infradna.tool.bridge_method_injector.WithBridgeMethods;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.Arrays.*;

/**
 * A repository on GitHub.
 * 
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"UnusedDeclaration"})
public class GHRepository {
    /*package almost final*/ GitHub root;

    private String description, homepage, name;
    private String url; // this is the API url
    private String html_url;    // this is the UI
    private GHUser owner;   // not fully populated. beware.
    private boolean has_issues, has_wiki, fork, _private, has_downloads;
    private int watchers,forks,open_issues,size;
    private String created_at, pushed_at;
    private Map<Integer,GHMilestone> milestones = new HashMap<Integer, GHMilestone>();
    
    private String master_branch,language;
    private Map<String,GHCommit> commits = new HashMap<String, GHCommit>();

    private GHRepoPermission permissions;

    private static class GHRepoPermission {
        boolean pull,push,admin;
    }


    public String getDescription() {
        return description;
    }

    public String getHomepage() {
        return homepage;
    }

    /**
     * URL of this repository, like 'http://github.com/kohsuke/jenkins'
     */
    public String getUrl() {
        return html_url;
    }

    /**
     * Gets the git:// URL to this repository, such as "git://github.com/kohsuke/jenkins.git"
     * This URL is read-only.
     */
    public String getGitTransportUrl() {
        return "git://github.com/"+getOwnerName()+"/"+name+".git";
    }

    /**
     * Gets the HTTPS URL to this repository, such as "https://github.com/kohsuke/jenkins.git"
     * This URL is read-only.
     */
    public String gitHttpTransportUrl() {
        return "https://github.com/"+getOwnerName()+"/"+name+".git";
    }

    /**
     * Short repository name without the owner. For example 'jenkins' in case of http://github.com/jenkinsci/jenkins
     */
    public String getName() {
        return name;
    }

    public boolean hasPullAccess() {
        return permissions!=null && permissions.pull;
    }

    public boolean hasPushAccess() {
        return permissions!=null && permissions.push;
    }

    public boolean hasAdminAccess() {
        return permissions!=null && permissions.admin;
    }

    /**
     * Gets the primary programming language.
     */
    public String getLanguage() {
        return language;
    }

    public GHUser getOwner() throws IOException {
        return root.getUser(owner.login);   // because 'owner' isn't fully populated
    }

    public List<GHIssue> getIssues(GHIssueState state) throws IOException {
       return Arrays.asList(GHIssue.wrap(root.retrieve("/repos/" + owner.login + "/" + name + "/issues?state=" + state.toString().toLowerCase(), GHIssue[].class), this));
    }

    protected String getOwnerName() {
        return owner.login;
    }

    public boolean hasIssues() {
        return has_issues;
    }

    public boolean hasWiki() {
        return has_wiki;
    }

    public boolean isFork() {
        return fork;
    }

    public int getForks() {
        return forks;
    }

    public boolean isPrivate() {
        return _private;
    }

    public boolean hasDownloads() {
        return has_downloads;
    }

    public int getWatchers() {
        return watchers;
    }

    public int getOpenIssueCount() {
        return open_issues;
    }

    /**
     *
     * @return
     *      null if the repository was never pushed at.
     */
    public Date getPushedAt() {
        return GitHub.parseDate(pushed_at);
    }

    public Date getCreatedAt() {
        return GitHub.parseDate(created_at);
    }

    /**
     * Returns the primary branch you'll configure in the "Admin > Options" config page.
     *
     * @return
     *      This field is null until the user explicitly configures the master branch.
     */
    public String getMasterBranch() {
        return master_branch;
    }

    public int getSize() {
        return size;
    }

    /**
     * Gets the collaborators on this repository.
     * This set always appear to include the owner.
     */
    @WithBridgeMethods(Set.class)
    public GHPersonSet<GHUser> getCollaborators() throws IOException {
        return new GHPersonSet<GHUser>(GHUser.wrap(root.retrieve("/repos/" + owner.login + "/" + name + "/collaborators", GHUser[].class),root));
    }

    /**
     * Gets the names of the collaborators on this repository.
     * This method deviates from the principle of this library but it works a lot faster than {@link #getCollaborators()}.
     */
    public Set<String> getCollaboratorNames() throws IOException {
        Set<String> r = new HashSet<String>();
        for (GHUser u : GHUser.wrap(root.retrieve("/repos/" + owner.login + "/" + name + "/collaborators", GHUser[].class),root))
            r.add(u.login);
        return r;
    }

    /**
     * If this repository belongs to an organization, return a set of teams.
     */
    public Set<GHTeam> getTeams() throws IOException {
        return Collections.unmodifiableSet(new HashSet<GHTeam>(Arrays.asList(GHTeam.wrapUp(root.retrieveWithAuth("/repos/" + owner.login + "/" + name + "/teams", GHTeam[].class), root.getOrganization(owner.login)))));
    }

    public void addCollaborators(GHUser... users) throws IOException {
        addCollaborators(asList(users));
    }

    public void addCollaborators(Collection<GHUser> users) throws IOException {
        modifyCollaborators(users, "PUT");
    }

    public void removeCollaborators(GHUser... users) throws IOException {
        removeCollaborators(asList(users));
    }

    public void removeCollaborators(Collection<GHUser> users) throws IOException {
        modifyCollaborators(users, "DELETE");
    }

    private void modifyCollaborators(Collection<GHUser> users, String method) throws IOException {
        verifyMine();
        for (GHUser user : users) {
            new Poster(root).withCredential().to("/repos/"+owner.login+"/"+name+"/collaborators/"+user.getLogin(),null,method);
        }
    }

    public void setEmailServiceHook(String address) throws IOException {
        WebClient wc = root.createWebClient();
        HtmlPage pg = (HtmlPage)wc.getPage(getUrl()+"/admin");
        HtmlInput email = (HtmlInput)pg.getElementById("email_address");
        email.setValueAttribute(address);
        HtmlCheckBoxInput active = (HtmlCheckBoxInput)pg.getElementById("email[active]");
        active.setChecked(true);

        final HtmlForm f = email.getEnclosingFormOrDie();
        f.submit((HtmlButton) f.getElementsByTagName("button").get(0));
    }

    private void edit(String key, String value) throws IOException {
        Poster poster = new Poster(root).withCredential();
        if (!key.equals("name"))
            poster.with("name", name);   // even when we don't change the name, we need to send it in
        poster.with(key, value)
                .to("/repos/" + owner.login + "/" + name, null, "PATCH");
    }

    /**
     * Enables or disables the issue tracker for this repository.
     */
    public void enableIssueTracker(boolean v) throws IOException {
        edit("has_issues", String.valueOf(v));
    }

    /**
     * Enables or disables Wiki for this repository.
     */
    public void enableWiki(boolean v) throws IOException {
        edit("has_wiki", String.valueOf(v));
    }

    public void enableDownloads(boolean v) throws IOException {
        edit("has_downloads",String.valueOf(v));
    }

    /**
     * Rename this repository.
     */
    public void renameTo(String name) throws IOException {
        edit("name",name);
    }

    public void setDescription(String value) throws IOException {
        edit("description",value);
    }

    public void setHomepage(String value) throws IOException {
        edit("homepage",value);
    }

    /**
     * Deletes this repository.
     */
    public void delete() throws IOException {
        new Poster(root).withCredential().to("/repos/" + owner.login +"/"+name, null, "DELETE");
    }

    /**
     * Forks this repository as your repository.
     *
     * @return
     *      Newly forked repository that belong to you.
     */
    public GHRepository fork() throws IOException {
        return new Poster(root).withCredential().to("/repos/" + owner.login + "/" + name + "/forks", GHRepository.class, "POST").wrap(root);
    }

    /**
     * Forks this repository into an organization.
     *
     * @return
     *      Newly forked repository that belong to you.
     */
    public GHRepository forkTo(GHOrganization org) throws IOException {
        new Poster(root).withCredential().to(String.format("/repos/%s/%s/forks?org=%s",owner.login,name,org.getLogin()));

        // this API is asynchronous. we need to wait for a bit
        for (int i=0; i<10; i++) {
            GHRepository r = org.getRepository(name);
            if (r!=null)    return r;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw (IOException)new InterruptedIOException().initCause(e);
            }
        }
        throw new IOException(this+" was forked into "+org.getLogin()+" but can't find the new repository");
    }

    /**
     * Retrieves a specified pull request.
     */
    public GHPullRequest getPullRequest(int i) throws IOException {
        return root.retrieveWithAuth("/repos/" + owner.login + '/' + name + "/pulls/" + i, GHPullRequest.class).wrapUp(this);
    }

    /**
     * Retrieves all the pull requests of a particular state.
     */
    public List<GHPullRequest> getPullRequests(GHIssueState state) throws IOException {
        GHPullRequest[] r = root.retrieveWithAuth("/repos/" + owner.login + '/' + name + "/pulls?state=" + state.name().toLowerCase(Locale.ENGLISH), GHPullRequest[].class);
        for (GHPullRequest p : r)
            p.wrapUp(this);
        return new ArrayList<GHPullRequest>(Arrays.asList(r));
    }

    /**
     * Retrieves the currently configured hooks.
     */
    public List<GHHook> getHooks() throws IOException {
        List<GHHook> list = new ArrayList<GHHook>(Arrays.asList(
                root.retrieveWithAuth(String.format("/repos/%s/%s/hooks", owner.login, name), GHHook[].class)));
        for (GHHook h : list)
            h.wrap(this);
        return list;
    }

    public GHHook getHook(int id) throws IOException {
        return root.retrieveWithAuth(String.format("/repos/%s/%s/hooks/%d", owner.login, name, id), GHHook.class).wrap(this);
    }

    /**
     * Gets a commit object in this repository.
     */
    public GHCommit getCommit(String sha1) throws IOException {
        GHCommit c = commits.get(sha1);
        if (c==null) {
            c = root.retrieve(String.format("/repos/%s/%s/commits/%s", owner.login, name, sha1), GHCommit.class).wrapUp(this);
            commits.put(sha1,c);
        }
        return c;
    }

    /**
     * Lists all the commits.
     */
    public PagedIterable<GHCommit> listCommits() {
        return new PagedIterable<GHCommit>() {
            public PagedIterator<GHCommit> iterator() {
                return new PagedIterator<GHCommit>(root.retrievePaged(String.format("/repos/%s/%s/commits",owner.login,name),GHCommit[].class,false)) {
                    protected void wrapUp(GHCommit[] page) {
                        for (GHCommit c : page)
                            c.wrapUp(GHRepository.this);
                    }
                };
            }
        };
    }

    /**
     * Lists up all the commit comments in this repository.
     */
    public PagedIterable<GHCommitComment> listCommitComments() {
        return new PagedIterable<GHCommitComment>() {
            public PagedIterator<GHCommitComment> iterator() {
                return new PagedIterator<GHCommitComment>(root.retrievePaged(String.format("/repos/%s/%s/comments",owner.login,name),GHCommitComment[].class,false)) {
                    @Override
                    protected void wrapUp(GHCommitComment[] page) {
                        for (GHCommitComment c : page)
                            c.wrap(GHRepository.this);
                    }
                };
            }
        };
    }

    /**
     * 
     * See https://api.github.com/hooks for possible names and their configuration scheme.
     * TODO: produce type-safe binding
     * 
     * @param name
     *      Type of the hook to be created. See https://api.github.com/hooks for possible names.
     * @param config
     *      The configuration hash.
     * @param events
     *      Can be null. Types of events to hook into.
     */
    public GHHook createHook(String name, Map<String,String> config, Collection<GHEvent> events, boolean active) throws IOException {
        List<String> ea = null;
        if (events!=null) {
            ea = new ArrayList<String>();
            for (GHEvent e : events)
                ea.add(e.name().toLowerCase(Locale.ENGLISH));
        }

        return new Poster(root)
                .withCredential()
                .with("name",name)
                .with("active", active)
                ._with("config", config)
                ._with("events",ea)
                .to(String.format("/repos/%s/%s/hooks",owner.login,this.name),GHHook.class).wrap(this);
    }
    
    public GHHook createWebHook(URL url, Collection<GHEvent> events) throws IOException {
        return createHook("web",Collections.singletonMap("url",url.toExternalForm()),events,true);
    }

    public GHHook createWebHook(URL url) throws IOException {
        return createWebHook(url,null);
    }

// this is no different from getPullRequests(OPEN)
//    /**
//     * Retrieves all the pull requests.
//     */
//    public List<GHPullRequest> getPullRequests() throws IOException {
//        return root.retrieveWithAuth("/pulls/"+owner+'/'+name,JsonPullRequests.class).wrap(root);
//    }

    private void verifyMine() throws IOException {
        if (!root.login.equals(owner.login))
            throw new IOException("Operation not applicable to a repository owned by someone else: "+owner.login);
    }

    /**
     * Returns a set that represents the post-commit hook URLs.
     * The returned set is live, and changes made to them are reflected to GitHub.
     * 
     * @deprecated 
     *      Use {@link #getHooks()} and {@link #createHook(String, Map, Collection, boolean)}
     */
    public Set<URL> getPostCommitHooks() {
        return postCommitHooks;
    }

    /**
     * Live set view of the post-commit hook.
     */
    private final Set<URL> postCommitHooks = new AbstractSet<URL>() {
        private List<URL> getPostCommitHooks() {
            try {
                List<URL> r = new ArrayList<URL>();
                for (GHHook h : getHooks()) {
                    if (h.getName().equals("web")) {
                        r.add(new URL(h.getConfig().get("url")));
                    }
                }
                return r;
            } catch (IOException e) {
                throw new GHException("Failed to retrieve post-commit hooks",e);
            }
        }

        @Override
        public Iterator<URL> iterator() {
            return getPostCommitHooks().iterator();
        }

        @Override
        public int size() {
            return getPostCommitHooks().size();
        }

        @Override
        public boolean add(URL url) {
            try {
                createWebHook(url);
                return true;
            } catch (IOException e) {
                throw new GHException("Failed to update post-commit hooks",e);
            }
        }

        @Override
        public boolean remove(Object url) {
            try {
                String _url = ((URL)url).toExternalForm();
                for (GHHook h : getHooks()) {
                    if (h.getName().equals("web") && h.getConfig().get("url").equals(_url)) {
                        h.delete();
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                throw new GHException("Failed to update post-commit hooks",e);
            }
        }
    };

    /*package*/ GHRepository wrap(GitHub root) {
        this.root = root;
        return this;
    }

    /**
     * Gets branches by {@linkplain GHBranch#getName() their names}.
     */
    public Map<String,GHBranch> getBranches() throws IOException {
        Map<String,GHBranch> r = new TreeMap<String,GHBranch>();
        for (GHBranch p : root.retrieve("/repos/" + owner.login + "/" + name + "/branches", GHBranch[].class)) {
            p.wrap(this);
            r.put(p.getName(),p);
        }
        return r;
    }

    public Map<Integer, GHMilestone> getMilestones() throws IOException {
        Map<Integer,GHMilestone> milestones = new TreeMap<Integer, GHMilestone>();
    	GHMilestone[] ms = root.retrieve("/repos/" + owner.login + "/" + name + "/milestones", GHMilestone[].class);
    	for (GHMilestone m : ms) {
    		m.owner = this;
    		m.root = root;
    		milestones.put(m.getNumber(), m);
    	}
    	return milestones;
    }

	public GHMilestone getMilestone(int number) throws IOException {
		GHMilestone m = milestones.get(number);
		if (m == null) {
			m = root.retrieve("/repos/" + owner.login + "/" + name + "/milestones/" + number, GHMilestone.class);
    		m.owner = this;
    		m.root = root;
			milestones.put(m.getNumber(), m);
		}
		return m;
	}
	
	public GHMilestone createMilestone(String title, String description) throws IOException {
        return new Poster(root).withCredential()
                .with("title", title).with("description", description)
                .to("/repos/"+owner.login+"/"+name+"/milestones", GHMilestone.class,"POST").wrap(this);
	}

    @Override
    public String toString() {
        return "Repository:"+owner.login+":"+name;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GHRepository) {
            GHRepository that = (GHRepository) obj;
            return this.owner.login.equals(that.owner.login)
                && this.name.equals(that.name);
        }
        return false;
    }
}
