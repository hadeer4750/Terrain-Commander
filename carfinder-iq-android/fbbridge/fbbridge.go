package fbbridge

import (
    "context"
    "encoding/json"
    "fmt"
    "strings"
    "time"

    "github.com/teslashibe/facebook-go/groups"
)

type SearchResult struct {
    GroupID    string `json:"groupId"`
    GroupName  string `json:"groupName"`
    AuthorName string `json:"authorName"`
    Message    string `json:"message"`
    CreatedAt  string `json:"createdAt"`
    URL        string `json:"url"`
}

func parseCookies(header string) groups.Cookies {
    vals := map[string]string{}
    for _, part := range strings.Split(header, ";") {
        kv := strings.SplitN(strings.TrimSpace(part), "=", 2)
        if len(kv) == 2 {
            vals[kv[0]] = kv[1]
        }
    }
    return groups.Cookies{
        SB: vals["sb"], DATR: vals["datr"], CUser: vals["c_user"],
        XS: vals["xs"], FR: vals["fr"], PSL: vals["ps_l"], PSN: vals["ps_n"],
    }
}

func normalizeTokens(q string) []string {
    q = strings.ToLower(strings.TrimSpace(q))
    repl := strings.NewReplacer(",", " ", ".", " ", "-", " ", "_", " ", "/", " ", "\\", " ")
    q = repl.Replace(q)
    var out []string
    seen := map[string]bool{}
    for _, t := range strings.Fields(q) {
        if len([]rune(t)) < 2 || seen[t] {
            continue
        }
        seen[t] = true
        out = append(out, t)
    }
    return out
}

func match(message string, tokens []string) bool {
    text := strings.ToLower(message)
    if len(tokens) == 0 {
        return false
    }
    hits := 0
    for _, t := range tokens {
        if strings.Contains(text, t) {
            hits++
        }
    }
    // Require most of the useful terms, but tolerate one missing term.
    need := len(tokens)
    if need > 2 {
        need--
    }
    return hits >= need
}

// JoinedGroups returns the authenticated user's joined Facebook groups as JSON.
// The cookie header comes from Android WebView's private Facebook cookie store.
func JoinedGroups(cookieHeader string) (string, error) {
    ck := parseCookies(cookieHeader)
    if ck.CUser == "" || ck.XS == "" {
        return "", fmt.Errorf("Facebook session is missing c_user/xs")
    }
    c, err := groups.New(ck)
    if err != nil {
        return "", err
    }
    ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
    defer cancel()
    gs, err := c.MyGroups(ctx)
    if err != nil {
        return "", err
    }
    b, err := json.Marshal(gs)
    if err != nil {
        return "", err
    }
    return string(b), nil
}

// SearchJoinedGroups scans the first page of posts in every joined group and
// returns posts matching the car query. It only reads content the logged-in user can access.
func SearchJoinedGroups(cookieHeader, query string) (string, error) {
    ck := parseCookies(cookieHeader)
    if ck.CUser == "" || ck.XS == "" {
        return "", fmt.Errorf("Facebook session is missing c_user/xs")
    }
    c, err := groups.New(ck, groups.WithMinRequestGap(900*time.Millisecond))
    if err != nil {
        return "", err
    }

    ctx, cancel := context.WithTimeout(context.Background(), 8*time.Minute)
    defer cancel()

    gs, err := c.MyGroups(ctx)
    if err != nil {
        return "", err
    }

    tokens := normalizeTokens(query)
    var results []SearchResult

    for _, g := range gs {
        if g.PendingJoin || !g.Joined {
            continue
        }
        page, err := c.GetGroupPosts(ctx, g.ID)
        if err != nil {
            continue
        }
        for _, p := range page.Posts {
            hay := p.Message + " " + strings.Join(p.Attachments, " ")
            if !match(hay, tokens) {
                continue
            }
            u := fmt.Sprintf("https://www.facebook.com/groups/%s/posts/%s", g.ID, p.ID)
            results = append(results, SearchResult{
                GroupID: g.ID, GroupName: g.Name, AuthorName: p.AuthorName,
                Message: p.Message, CreatedAt: p.CreatedAt.Format(time.RFC3339), URL: u,
            })
        }
    }

    b, err := json.Marshal(results)
    if err != nil {
        return "", err
    }
    return string(b), nil
}
